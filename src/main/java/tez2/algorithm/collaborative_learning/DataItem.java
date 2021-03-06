package tez2.algorithm.collaborative_learning;

import burlap.oomdp.core.states.State;

/**
 * Created by suat on 17-May-17.
 */
public class DataItem {
    private State state;
    private String actionName;

    public DataItem(State state, String actionName) {
        this.state = state;
        this.actionName = actionName;
    }

    public State getState() {
        return state;
    }

    public String getActionName() {
        return actionName;
    }
}
