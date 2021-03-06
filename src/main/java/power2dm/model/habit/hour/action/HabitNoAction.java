package power2dm.model.habit.hour.action;

import burlap.oomdp.core.Domain;
import burlap.oomdp.core.TransitionProbability;
import burlap.oomdp.core.objects.ObjectInstance;
import burlap.oomdp.core.states.State;
import burlap.oomdp.singleagent.FullActionModel;
import burlap.oomdp.singleagent.GroundedAction;
import burlap.oomdp.singleagent.common.SimpleAction;
import power2dm.model.habit.hour.HabitP2DMDomain;
import power2dm.model.habit.hour.HabitP2DMEnvironmentSimulator;

import java.util.ArrayList;
import java.util.List;

import static power2dm.model.habit.hour.HabitP2DMDomain.*;

/**
 * Created by suat on 08-Apr-16.
 */
public class HabitNoAction extends SimpleAction implements FullActionModel {

    public HabitNoAction(String name, Domain domain) {
        super(name, domain);
    }

    @Override
    protected State performActionHelper(State s, GroundedAction groundedAction) {
        HabitP2DMEnvironmentSimulator simulator = ((HabitP2DMDomain) domain).getEnvironmentSimulator();
        simulator.updateEnvironment(s, groundedAction);
        return getNextState(s, groundedAction);
    }

    public List<TransitionProbability> getTransitions(State s, GroundedAction groundedAction) {
        List<TransitionProbability> probabilities = new ArrayList<TransitionProbability>();
        State ns = getNextState(s, groundedAction);
        TransitionProbability tp = new TransitionProbability(ns, 1.0);
        probabilities.add(tp);
        return probabilities;
    }

    private State getNextState(State s, GroundedAction groundedAction) {
        State ns = s.copy();
        ObjectInstance state = ns.getFirstObjectOfClass(CLASS_STATE);
        int timing = state.getIntValForAttribute(ATT_HOUR_OF_DAY);

        // update the state by updating state's parameters
        HabitP2DMEnvironmentSimulator simulator = ((HabitP2DMDomain) domain).getEnvironmentSimulator();
        ns = ns.setObjectsValue(state.getName(), ATT_HOUR_OF_DAY, timing + 1);
        ns = ns.setObjectsValue(state.getName(), ATT_HABIT_COEFF, simulator.getHabitGainRatio());
        ns = ns.setObjectsValue(state.getName(), ATT_CAL_INTAKE_ENTRY, simulator.getCalorieIntakeEntry());
//        s = s.setObjectsValue(state.getName(), ATT_LOCATION, simulator.getContext().ordinal());

        return ns;
    }
}
