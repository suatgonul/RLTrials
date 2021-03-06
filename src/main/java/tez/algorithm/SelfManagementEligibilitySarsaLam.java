package tez.algorithm;

import burlap.behavior.policy.Policy;
import burlap.behavior.policy.SolverDerivedPolicy;
import burlap.behavior.singleagent.EpisodeAnalysis;
import burlap.behavior.singleagent.learning.tdmethods.SarsaLam;
import burlap.behavior.singleagent.options.Option;
import burlap.behavior.singleagent.options.support.EnvironmentOptionOutcome;
import burlap.behavior.valuefunction.QValue;
import burlap.oomdp.core.Domain;
import burlap.oomdp.core.states.State;
import burlap.oomdp.singleagent.Action;
import burlap.oomdp.singleagent.GroundedAction;
import burlap.oomdp.singleagent.common.SimpleGroundedAction;
import burlap.oomdp.singleagent.environment.Environment;
import burlap.oomdp.singleagent.environment.EnvironmentOutcome;
import burlap.oomdp.statehashing.HashableState;
import burlap.oomdp.statehashing.HashableStateFactory;
import org.apache.log4j.Logger;
import tez.algorithm.collaborative_learning.SparkStateClassifier;
import tez.domain.ExtendedEnvironmentOutcome;
import tez.domain.SelfManagementRewardFunction;
import tez.domain.action.SelfManagementAction;
import tez.environment.real.RealWorld;
import tez.experiment.debug.StepPrinter;
import tez.experiment.performance.SelfManagementEligibilityEpisodeAnalysis;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static tez.domain.SelfManagementDomainGenerator.ACTION_INT_DELIVERY;
import static tez.util.LogUtil.*;

/**
 * Created by suatgonul on 5/1/2017.
 */
public class SelfManagementEligibilitySarsaLam extends SarsaLam {
    private static final Logger log = Logger.getLogger(SelfManagementEligibilitySarsaLam.class);

    private boolean useStateClassifier = false;
    private List<State> deliveredInterventions;

    public SelfManagementEligibilitySarsaLam(Domain domain, double gamma, HashableStateFactory hashingFactory, double qInit, double learningRate, Policy learningPolicy, int maxEpisodeSize, double lambda, boolean useStateClassifier) {
        super(domain, gamma, hashingFactory, qInit, learningRate, learningPolicy, maxEpisodeSize, lambda);
        if (learningPolicy instanceof SolverDerivedPolicy) {
            ((SolverDerivedPolicy) learningPolicy).setSolver(this);
        }
        this.useStateClassifier = useStateClassifier;
    }

    @Override
    public EpisodeAnalysis runLearningEpisode(Environment env, int maxSteps) {
        deliveredInterventions = new ArrayList<>();

        State initialState = env.getCurrentObservation();

        SelfManagementEligibilityEpisodeAnalysis ea = new SelfManagementEligibilityEpisodeAnalysis(initialState);
        maxQChangeInLastEpisode = 0.;

        HashableState curState = this.stateHash(initialState);
        eStepCounter = 0;
        LinkedList<SelfManagementEligibilityTrace> traces = new LinkedList<>();

        GroundedAction action = (GroundedAction) learningPolicy.getAction(curState.s);
        QValue curQ = this.getQ(curState, action);

        while (!env.isInTerminalState() && (eStepCounter < maxSteps || maxSteps == -1)) {

            List<QValue> currentQVals = copyCurrentQVals(this.qIndex.get(curState).qEntry);
            SelfManagementAction.SelectedBy selectedBy = ((SelfManagementSimpleGroundedAction) action).getSelectedBy();
            if (useStateClassifier && selectedBy == SelfManagementAction.SelectedBy.RANDOM) {
                //Action guessedAction = H2OStateClassifier.getInstance().guessAction(curState.s);
                Action guessedAction = SparkStateClassifier.getInstance().guessAction(curState.s);
                if (guessedAction != null) {
                    action = guessedAction.getGroundedAction();
                    selectedBy = SelfManagementAction.SelectedBy.STATE_CLASSIFIER;
                }
            }

            if (env instanceof RealWorld) {
                log_info(log, ((RealWorld) env).getDeviceIdentifier(), " Action " + action.actionName() + " selected");
            }


            if (action.actionName().equals(ACTION_INT_DELIVERY)) {
                deliveredInterventions.add(curState);
            }

            EnvironmentOutcome eo = action.executeIn(env);
            ExtendedEnvironmentOutcome eeo = (ExtendedEnvironmentOutcome) eo;

            HashableState nextState = this.stateHash(eo.op);
            GroundedAction nextAction = (GroundedAction) learningPolicy.getAction(nextState.s);

            QValue nextQ = this.getQ(nextState, nextAction);
            double nextQV = nextQ.q;

            if (env.isInTerminalState()) {
                nextQV = 0.;
            }


            //manage option specifics
            double r = eo.r;
            double discount = eo instanceof EnvironmentOptionOutcome ? ((EnvironmentOptionOutcome) eo).discount : this.gamma;
            int stepInc = eo instanceof EnvironmentOptionOutcome ? ((EnvironmentOptionOutcome) eo).numSteps : 1;
            eStepCounter += stepInc;

            //delta
            double delta = r + (discount * nextQV) - curQ.q;

            //update all states visited in this episode so far
            boolean foundCurrentQTrace = false;
            for (SelfManagementEligibilityTrace et : traces) {

                if (et.sh.equals(curState)) {
                    if (et.q.a.equals(action)) {
                        foundCurrentQTrace = true;
                        //et.eligibility = 1.; //replacing traces
                        et.eligibility = et.eligibility + 1;
                        System.out.println("Encountered previous state: " + curState);
                    } else {
                        et.eligibility = 0.; //replacing traces
                    }
                }

                double learningRate = this.learningRate.pollLearningRate(this.totalNumberOfSteps, et.sh.s, et.q.a);

                // if the user reaction is positive at the current state and if the current trace includes an
                // intervention delivery action do not positively reward a previous trace if it did not provide a
                // positive result.
                if (eeo.getUserReaction() && et.q.a.actionName().equals(ACTION_INT_DELIVERY)) {
                    if (!et.isUseful()) {
                        double tempDelta = SelfManagementRewardFunction.getRewardNonReactionToIntervention() + (discount * nextQV) - curQ.q;
                        et.q.q = et.q.q + (learningRate * et.eligibility * tempDelta);
                        et.eligibility = et.eligibility * lambda * discount;

                    } else {
                        et.q.q = et.q.q + (learningRate * et.eligibility * delta);
                        et.eligibility = et.eligibility * lambda * discount;
                    }

                    //continue;
                } else {
                    // if the user reaction is negative then apply regular eligibility traces
                    et.q.q = et.q.q + (learningRate * et.eligibility * delta);
                    et.eligibility = et.eligibility * lambda * discount;
                }

                double deltaQ = Math.abs(et.initialQ - et.q.q);
                if (deltaQ > maxQChangeInLastEpisode) {
                    maxQChangeInLastEpisode = deltaQ;
                }

            }

            log_generic(log, "Eligibility values calculated");

            String interference = "N";
            if (!foundCurrentQTrace) {
                //then update and add it
                double learningRate = this.learningRate.pollLearningRate(this.totalNumberOfSteps, curQ.s, curQ.a);

                SelfManagementEligibilityTrace et;

                if (eeo.getUserReaction()) {
                    if (action.actionName().equals(ACTION_INT_DELIVERY)) {
                        curQ.q = curQ.q + (learningRate * delta);
                        et = new SelfManagementEligibilityTrace(curState, curQ, lambda * discount, true);
                        interference = "NoN"; //no need

                    } else if (deliveredInterventions.size() > 0) {
                        // override delta for the simulated action
                        Action intDeliveryAction = null;
                        for (Action a : actions) {
                            if (a.getName().equals(ACTION_INT_DELIVERY)) {
                                intDeliveryAction = a;
                            }
                        }

                        action = new SimpleGroundedAction(intDeliveryAction);
                        QValue simCurQ = this.getQ(curState, action);
                        r = SelfManagementRewardFunction.getRewardReactionToIntervention();
                        delta = r + (discount * nextQV) - simCurQ.q;
                        simCurQ.q = simCurQ.q + (learningRate * delta);

                        et = new SelfManagementEligibilityTrace(curState, simCurQ, lambda * discount, true);
                        interference = "Y";
                    } else {
                        curQ.q = curQ.q + (learningRate * delta);
                        et = new SelfManagementEligibilityTrace(curState, curQ, lambda * discount, false);
                    }

                } else {
                    curQ.q = curQ.q + (learningRate * delta);
                    et = new SelfManagementEligibilityTrace(curState, curQ, lambda * discount, false);
                }

                traces.add(et);
                log_generic(log, "Backward rewarding is done");

                if (action.action.isPrimitive() || !this.shouldAnnotateOptions) {
                    ea.recordTransitionTo(action, nextState.s, r, currentQVals, eeo.getUserContext(), eeo.getUserReaction(), interference, selectedBy);
                    StepPrinter.printStep(action, curState.s, r, currentQVals, eeo.getUserContext(), eeo.getUserReaction(), interference, selectedBy);
                } else {
                    ea.appendAndMergeEpisodeAnalysis(((Option) action.action).getLastExecutionResults());
                }

                double deltaQ = Math.abs(et.initialQ - et.q.q);
                if (deltaQ > maxQChangeInLastEpisode) {
                    maxQChangeInLastEpisode = deltaQ;
                }

            }

            log_generic(log, "Moving to next step");

            //move on
            curState = nextState;
            action = nextAction;
            curQ = nextQ;

            if (eeo.getUserReaction()) {
                deliveredInterventions = new ArrayList<>();
            }

            this.totalNumberOfSteps++;

        }


        if (episodeHistory.size() >= numEpisodesToStore) {
            episodeHistory.poll();
        }
        episodeHistory.offer(ea);

        return ea;
    }

    protected List<QValue> copyCurrentQVals(List<QValue> qValues) {
        List<QValue> copyList = new ArrayList<>();
        for (QValue qv : qValues) {
            copyList.add(new QValue(qv));
        }
        return copyList;
    }
}
