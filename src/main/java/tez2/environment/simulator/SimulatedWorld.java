package tez2.environment.simulator;

import burlap.oomdp.core.Domain;
import burlap.oomdp.core.TerminalFunction;
import burlap.oomdp.core.objects.MutableObjectInstance;
import burlap.oomdp.core.objects.ObjectInstance;
import burlap.oomdp.core.states.MutableState;
import burlap.oomdp.core.states.State;
import burlap.oomdp.singleagent.GroundedAction;
import burlap.oomdp.singleagent.RewardFunction;
import burlap.oomdp.singleagent.environment.EnvironmentObserver;
import burlap.oomdp.singleagent.environment.EnvironmentOutcome;
import burlap.oomdp.statehashing.HashableState;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import tez2.algorithm.jitai_selection.JitaiSelectionQLearning;
import tez2.domain.ExtendedEnvironmentOutcome;
import tez2.domain.SelfManagementDomain;
import tez2.domain.TerminalState;
import tez2.environment.SelfManagementEnvironment;
import tez2.environment.context.*;
import tez2.experiment.performance.SelfManagementEpisodeAnalysis;
import tez2.persona.ActionPlan;
import tez2.persona.Activity;
import tez2.persona.TimePlan;
import tez2.persona.parser.PersonaParser;
import tez2.persona.parser.PersonaParserException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static tez2.domain.DomainConfig.*;

/**
 * Created by suatgonul on 12/2/2016.
 */
public class SimulatedWorld extends SelfManagementEnvironment {
    /*
     * dynamically updated values throughout the simulation of activities
     */
    private int currentDay;
    private TimePlan currentTimePlan;
    private Activity currentActivity;
    private boolean lastActivity;
    private boolean behaviorPerformed;
    private int suitableActivityCount;
    private int processedActivityCountForBehavior;


    private String personaFolder;

    // jitai selection related objects
    private JitaiSelectionEnvironment jitaiSelectionEnvironment;
    private JitaiSelectionQLearning jitaiSelectionLearning;
    private List<SelfManagementEpisodeAnalysis> jitaiSelectionResults = new ArrayList<>();
    private SelfManagementEpisodeAnalysis jitaiSelectionEpisode;
    private int checkedActionPlanIndex;
    private ActionPlan actionPlan;
    private GroundedAction lastSelectedJitai;
    private Map<String, Double> jitaiPreferences;


    public SimulatedWorld(Domain domain, RewardFunction rf, TerminalFunction tf, int stateChangeFrequency, String personaFolder, JitaiSelectionEnvironment jitaiSelectionEnvironment, JitaiSelectionQLearning jitaiSelectionLearning) {
        super(domain, rf, tf, stateChangeFrequency);
        this.personaFolder = personaFolder;
        this.currentDay = 1;
        this.jitaiSelectionLearning = jitaiSelectionLearning;
        this.jitaiSelectionEnvironment = jitaiSelectionEnvironment;
        initEpisode();
        this.curState = stateGenerator.generateState();
        initActionPlan();
        initJitaiPreferences(personaFolder);
    }

    /**
     * Main orchestrator call for executing a single step of an episode. Within this method, by calling the executeIn
     * method of the environment state is set to the next state and afterwards reward is obtained by calling the
     * reward function's reward method.
     *
     * @param ga
     * @return
     */
    @Override
    public EnvironmentOutcome executeAction(GroundedAction ga) {

        GroundedAction simGA = (GroundedAction) ga.copy();
        simGA.action = this.domain.getAction(ga.actionName());
        if (simGA.action == null) {
            throw new RuntimeException("Cannot execute action " + ga.toString() + " in this SimulatedEnvironment because the action is to known in this Environment's domain");
        }

        for (EnvironmentObserver observer : this.observers) {
            observer.observeEnvironmentActionInitiation(this.getCurrentObservation(), ga);
        }

        State nextState;
        if (this.allowActionFromTerminalStates || !this.isInTerminalState()) {
            // advances the time plan
            nextState = simGA.executeIn(this.curState);

            // generates the reward based on the reaction of the user
            this.lastReward = this.rf.reward(this.curState, simGA, nextState);
        } else {
            nextState = this.curState;
            this.lastReward = 0.;
        }

        EnvironmentOutcome eo = new ExtendedEnvironmentOutcome(this.curState.copy(), simGA, nextState.copy(), this.lastReward, this.tf.isTerminal(nextState), null, false);

        this.curState = nextState;

        for (EnvironmentObserver observer : this.observers) {
            observer.observeEnvironmentInteraction(eo);
        }

        return eo;
    }


    /**
     * Increase the time in real world by considering the start of the next activity and state time period
     */
    @Override
    public State getNextState(GroundedAction action) {
        advanceTimePlan();
        State state = getStateFromCurrentContext();
        return state;
    }

    private void advanceTimePlan() {
        // execute the jitai selection step
        List<Object> currentRange = getTimeRange();
        if (currentRange != null) {
            // initialize the jitai selection environment state
            if (checkedActionPlanIndex == -1) {
                jitaiSelectionResults.add(jitaiSelectionEpisode);
                jitaiSelectionEpisode = new SelfManagementEpisodeAnalysis(jitaiSelectionEnvironment.getCurrentObservation());
                jitaiSelectionEnvironment.initEpisode();
            }

            // processing the first activity overlapping with this time range
            int rangeIndex = (Integer) currentRange.get(0);
            if (rangeIndex > checkedActionPlanIndex) {
                behaviorPerformed = false;
                suitableActivityCount = 0;
                processedActivityCountForBehavior = 0;

                ActionPlan.JitaiTimeRange timeRange = (ActionPlan.JitaiTimeRange) currentRange.get(1);
                HashableState curJitaiSelectionState = jitaiSelectionLearning.stateHash(jitaiSelectionEnvironment.getCurrentObservation());
                jitaiSelectionLearning.executeLearningStep(jitaiSelectionEnvironment, curJitaiSelectionState, jitaiSelectionEpisode);
                lastSelectedJitai = jitaiSelectionEnvironment.getLastAction();
                checkedActionPlanIndex++;

                // check the last time range
                if (checkedActionPlanIndex + 1 == actionPlan.getJitaiTimeRanges().size()) {
                    checkedActionPlanIndex = -1;
                } else {
                    if(checkedActionPlanIndex % 2 == 0) {
                        // find the number of activities in which the behavior could be performed
                        LocalTime time = currentTimePlan.getStart().toLocalTime();
                        LocalTime timeRangeStart = actionPlan.getJitaiTimeRanges().get(checkedActionPlanIndex ).getStartTime();
                        LocalTime timeRangeEnd = actionPlan.getJitaiTimeRanges().get(checkedActionPlanIndex ).getEndTime();


                        for (int i = 0; i < currentTimePlan.getActivities().size(); i++) {
                            time = time.plusMinutes(currentTimePlan.getActivities().get(i).getDuration());
                            if (time.isAfter(timeRangeStart) && time.isBefore(timeRangeEnd)) {
                                if(currentTimePlan.getActivities().get(i).isSuitableForBehavior()) {
                                    suitableActivityCount++;
                                }
                            }
                        }
                    }
                }
            }
        }

        if(!lastSelectedJitai.actionName().contentEquals(ACTION_NO_ACTION)) {
            // based on the yes/no decision on delivering
            simulateUserReactionToJitai();

            if(checkedActionPlanIndex % 2 == 0 && currentActivity.isSuitableForBehavior()) {
                // perform reaction to the JITAI
                simulateBehaviorPerformance();
            }
        }

        int currentActivityIndex = currentTimePlan.getActivities().indexOf(currentActivity);
        DateTime activityEndTime = currentActivity.getEndTime();
        if (activityEndTime.isAfter(currentTime.plusMinutes(stateChangeFrequency))) {
            currentTime = currentTime.plusMinutes(stateChangeFrequency);
        } else {
            currentTime = activityEndTime;
            // update activity
            lastActivity = currentActivityIndex == currentTimePlan.getActivities().size() - 1;
            if (!lastActivity) {
                currentActivity = currentTimePlan.getActivities().get(++currentActivityIndex);
            }
        }
    }

    private boolean simulateUserReactionToJitai() {
        int dayType = getDayType(currentDay);
        Location location = currentActivity.getContext().getLocation();
        PhysicalActivity physicalActivity = currentActivity.getContext().getPhysicalActivity();
        StateOfMind stateOfMind = currentActivity.getContext().getStateOfMind();
        EmotionalStatus emotionalStatus = currentActivity.getContext().getEmotionalStatus();
        PhoneUsage phoneUsage = currentActivity.getContext().getPhoneUsage();

        // check performance of the behavior for the reminder type jitais
        if(checkedActionPlanIndex % 2 == 0) {
            if(behaviorPerformed == true) {
                return false;
            }
        }

        // check preferences on jitais
        if(new Random().nextDouble() > jitaiPreferences.get(lastSelectedJitai.actionName())) {
            return false;
        }

        // check suitability of the context
        if (currentActivity.getContext().getPhoneCheckSuitability() == true) {
            boolean contextSuitable = false;

            // before going to work and at the beginning of the working day
            if ((stateOfMind == StateOfMind.CALM || stateOfMind == StateOfMind.FOCUS) &&
                    (emotionalStatus == EmotionalStatus.NEUTRAL || emotionalStatus == EmotionalStatus.RELAXED || emotionalStatus == EmotionalStatus.HAPPY) &&
                    physicalActivity == PhysicalActivity.SITTING_IN_CAR || physicalActivity == PhysicalActivity.SEDENTARY) {
                contextSuitable = true;
            }

            // check other heuristics related to reaction to a delivered intervention
            if (contextSuitable) {
                // check the time between two reactions is less than 3 hours
//                if (lastInterventionCheckTime != null && time.getMillis() - lastInterventionCheckTime.getMillis() < 3 * 60 * 60 * 1000) {
//                    return false;
//                } else {
//                    lastInterventionCheckTime = time;
//                    return true;
//                }
                return true;
            } else {
                return false;
            }

        } else {
            return false;
        }
    }

    @Override
    public State getStateFromCurrentContext() {
        SelfManagementDomain smdomain = (SelfManagementDomain) domain;
        State s;
        if (!lastActivity) {
            s = new MutableState();
            s.addObject(new MutableObjectInstance(domain.getObjectClass(CLASS_STATE), CLASS_STATE));

            ObjectInstance o = s.getObjectsOfClass(CLASS_STATE).get(0);
            o.setValue(ATT_LOCATION, currentActivity.getContext().getLocation().ordinal());
            o.setValue(ATT_ACTIVITY_TIME, getQuarterStateRepresentation());
            o.setValue(ATT_ACTIVITY, currentActivity.getContext().getPhysicalActivity().ordinal());
            o.setValue(ATT_PHONE_USAGE, currentActivity.getContext().getPhoneUsage().ordinal());
            o.setValue(ATT_EMOTIONAL_STATUS, currentActivity.getContext().getEmotionalStatus().ordinal());

        } else {
            s = new TerminalState();
        }

        return s;
    }

    void simulateBehaviorPerformance() {
        // apply gaussian distribution to select
        NormalDistribution nd = new NormalDistribution();
        double sample = nd.sample();
        double cumulativeProbability = nd.cumulativeProbability(sample);
        processedActivityCountForBehavior++;
        double probabilityOfPerformanceInCurrentStep = (double) processedActivityCountForBehavior / (double) suitableActivityCount;
        System.out.println("processedActivityCountForBehavior: " + processedActivityCountForBehavior);
        System.out.println("probabilityOfPerformanceInCurrentStep: " + probabilityOfPerformanceInCurrentStep);

        if(cumulativeProbability < (probabilityOfPerformanceInCurrentStep)) {
            behaviorPerformed = true;
        }
    }

    public static void main(String [] srgs ) {
        NormalDistribution nd = new NormalDistribution();
        for(int i=0; i<10; i++) {
            double sample = nd.sample();
            System.out.println(sample);
            System.out.println(nd.cumulativeProbability(sample));
        }
//        System.out.println(nd.cumulativeProbability(1.0));
//        System.out.println(nd.cumulativeProbability(-1.0));
//        System.out.println(nd.inverseCumulativeProbability(0.25));
//        System.out.println(nd.inverseCumulativeProbability(0.5));
    }

    /**
     * Initializes the variables that are reset for each episode. Variables that preserve the value across episodes
     * are not changed in this method such as habit strength.
     *
     * @throws WorldSimulationException
     */
    protected void initEpisode() throws WorldSimulationException {
        // initialize time plan
        PersonaParser personaParser = new PersonaParser();
        String personaPath = personaFolder + "/weekdayv2.csv";

        try {
            currentTimePlan = personaParser.getTimePlanForPersona(personaPath);
        } catch (PersonaParserException e) {
            System.out.println("Could get time plan for day of week: " + currentDay);
            throw new WorldSimulationException("Could get time plan for day of week: " + currentDay, e);
        }

        // initialize time
        currentTime = currentTimePlan.getStart();
        // initialize activity
        currentActivity = currentTimePlan.getActivities().get(0);

        // All activities are processed. Set lastActivity to false to set the initial state of the next episode properly
        lastActivity = false;

        behaviorPerformed = false;
    }

    @Override
    public void resetEnvironment() {
        currentDay++;
        initEpisode();
        super.resetEnvironment();
    }

    private void initActionPlan() {
        actionPlan = new ActionPlan();
        // rem1 time range
        LocalTime time = new LocalTime().withMillisOfDay(21600000); // 6 o'clock
        ActionPlan.JitaiTimeRange tr = new ActionPlan.JitaiTimeRange(time, time.plusHours(2), ActionPlan.JitaiNature.REMINDER);
        actionPlan.addJITAITimeRange(tr);

        // mot1 time range
        time = time.plusHours(2);
        tr = new ActionPlan.JitaiTimeRange(time, time.plusHours(5), ActionPlan.JitaiNature.MOTIVATION);
        actionPlan.addJITAITimeRange(tr);

        // rem2 time range
        time = time.plusHours(5);
        tr = new ActionPlan.JitaiTimeRange(time, time.plusHours(2), ActionPlan.JitaiNature.REMINDER);
        actionPlan.addJITAITimeRange(tr);

        // mot2 time range
        time = time.plusHours(2);
        tr = new ActionPlan.JitaiTimeRange(time, time.plusHours(5), ActionPlan.JitaiNature.MOTIVATION);
        actionPlan.addJITAITimeRange(tr);

        // rem3 time range
        time = time.plusHours(5);
        tr = new ActionPlan.JitaiTimeRange(time, time.plusHours(2), ActionPlan.JitaiNature.REMINDER);
        actionPlan.addJITAITimeRange(tr);

        // mot3 time range
        time = time.plusHours(2);
        tr = new ActionPlan.JitaiTimeRange(time, time.plusHours(5), ActionPlan.JitaiNature.MOTIVATION);
        actionPlan.addJITAITimeRange(tr);
    }

    private void initJitaiPreferences(String personaFolder) {
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream(personaFolder + "/config"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file");
        }

        String[] preferences = prop.getProperty("reaction_to_jitais").split(",");
        jitaiPreferences = new HashMap<>();
        for(int i = 0; i<preferences.length; i++) {
            jitaiPreferences.put("JITAI_" + (i+1), Double.parseDouble(preferences[i]));
        }
    }

    private List<Object> getTimeRange() {
        for (int i = 0; i < actionPlan.getJitaiTimeRanges().size(); i++) {
            ActionPlan.JitaiTimeRange tr = actionPlan.getJitaiTimeRanges().get(i);
            if (currentTime.toLocalTime().isAfter(tr.getStartTime()) && currentTime.toLocalTime().isBefore(tr.getEndTime())) {
                List<Object> result = new ArrayList<>();
                result.add(i);
                result.add(tr);
                return result;
            }
        }
        return null;
    }

    public DynamicSimulatedWorldContext getContext() {
        DynamicSimulatedWorldContext context = new DynamicSimulatedWorldContext();
        context.setActivity(currentActivity);
        context.setCurrentDayType(getDayType(currentDay));
        context.setCurrentDayPart(getDayPart());
        context.setExpectedJitaiNature(((ActionPlan.JitaiTimeRange) getTimeRange().get(1)).getJitaiNature());
        return context;
    }

    public DynamicSimulatedWorldContext getLastContextForJitai(int jitaiOffset) {
        DynamicSimulatedWorldContext context = new DynamicSimulatedWorldContext();
        context.setCurrentDayType(getDayType(currentDay));
        context.setExpectedJitaiNature(((ActionPlan.JitaiTimeRange) getTimeRange().get(1)).getJitaiNature());

        LocalTime time = currentTimePlan.getStart().toLocalTime();
        LocalTime timeRangeEnd = actionPlan.getJitaiTimeRanges().get(jitaiOffset).getEndTime();
        for (int i = 0; i < currentTimePlan.getActivities().size(); i++) {
            time = time.plusMinutes(currentTimePlan.getActivities().get(i).getDuration());
            if (time.isEqual(timeRangeEnd) || time.isAfter(timeRangeEnd)) {
                context.setActivity(currentTimePlan.getActivities().get(i));
                context.setCurrentDayPart(getDayPart(time));
                break;
            }
        }
        return context;
    }

    public static class DynamicSimulatedWorldContext {
        private Activity activity;
        private int currentDayPart;
        private int currentDayType;
        private ActionPlan.JitaiNature expectedJitaiNature;

        public Activity getActivity() {
            return activity;
        }

        public void setActivity(Activity activity) {
            this.activity = activity;
        }

        public int getCurrentDayPart() {
            return currentDayPart;
        }

        public void setCurrentDayPart(int currentDayPart) {
            this.currentDayPart = currentDayPart;
        }

        public int getCurrentDayType() {
            return currentDayType;
        }

        public void setCurrentDayType(int currentDayType) {
            this.currentDayType = currentDayType;
        }

        public ActionPlan.JitaiNature getExpectedJitaiNature() {
            return expectedJitaiNature;
        }

        public void setExpectedJitaiNature(ActionPlan.JitaiNature expectedJitaiNature) {
            this.expectedJitaiNature = expectedJitaiNature;
        }
    }
}
