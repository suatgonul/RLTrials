package tez2.domain.js;

import burlap.oomdp.core.Domain;
import burlap.oomdp.core.states.State;
import burlap.oomdp.singleagent.GroundedAction;
import tez2.algorithm.ActionRestrictingState;
import tez2.domain.SelfManagementAction;
import tez2.persona.ActionPlan;

/**
 * Created by suat on 08-Apr-16.
 */
public class Jitai1Action extends SelfManagementAction {

    public Jitai1Action(String name, Domain domain) {
        super(name, domain);
    }

    @Override
    public boolean applicableInState(State s, GroundedAction groundedAction) {
        ActionRestrictingState ars = (ActionRestrictingState) s;
        if(ars.getExpectedJitaiType() == ActionPlan.JitaiNature.REMINDER) {
            return true;
        } else {
            return false;
        }
    }
}
