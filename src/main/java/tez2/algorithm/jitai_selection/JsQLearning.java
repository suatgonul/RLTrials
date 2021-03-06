package tez2.algorithm.jitai_selection;

import burlap.behavior.policy.Policy;
import burlap.behavior.policy.SolverDerivedPolicy;
import burlap.behavior.singleagent.learning.tdmethods.QLearning;
import burlap.behavior.singleagent.options.Option;
import burlap.behavior.singleagent.options.support.EnvironmentOptionOutcome;
import burlap.behavior.valuefunction.QValue;
import burlap.oomdp.core.Domain;
import burlap.oomdp.singleagent.Action;
import burlap.oomdp.singleagent.GroundedAction;
import burlap.oomdp.singleagent.environment.Environment;
import burlap.oomdp.singleagent.environment.EnvironmentOutcome;
import burlap.oomdp.statehashing.HashableState;
import burlap.oomdp.statehashing.HashableStateFactory;
import org.apache.hadoop.util.hash.Hash;
import tez2.algorithm.ActionRestrictingState;
import tez2.algorithm.SelfManagementSimpleGroundedAction;
import tez2.algorithm.collaborative_learning.StateClassifier;
import tez2.algorithm.collaborative_learning.js.SparkJsStateClassifier;
import tez2.domain.SelfManagementAction;
import tez2.domain.js.JsEnvironmentOutcome;
import tez2.experiment.performance.js.JsEpisodeAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by suat on 07-Feb-18.
 */
public class JsQLearning extends QLearning {
    protected GroundedAction lastSelectedAction;
    private GroundedAction lastSelectedActionCopy;

    public JsQLearning(Domain domain, double gamma, HashableStateFactory hashingFactory,
                       double qInit, double learningRate, Policy learningPolicy, int maxEpisodeSize) {
        super(domain, gamma, hashingFactory, qInit, learningRate, learningPolicy, maxEpisodeSize);
        if (learningPolicy instanceof SolverDerivedPolicy) {
            ((SolverDerivedPolicy) learningPolicy).setSolver(this);
        }
    }

    public GroundedAction selectAction(HashableState curState) {
        lastSelectedAction = (SelfManagementSimpleGroundedAction) learningPolicy.getAction(curState.s);
        lastSelectedActionCopy = lastSelectedAction.copy();

        SelfManagementAction.SelectedBy selectedBy = ((SelfManagementSimpleGroundedAction) lastSelectedAction).getSelectedBy();
        if ((StateClassifier.classifierModeIncludes("use")  || StateClassifier.classifierModeIncludes("use-js")) && selectedBy == SelfManagementAction.SelectedBy.RANDOM) {
            //Action guessedAction = H2OStateClassifier.getInstance().guessAction(curState.s);
            Action guessedAction = SparkJsStateClassifier.getInstance().guessAction(curState.s);
            if (guessedAction != null) {
                if(this.getQ(curState, guessedAction.getGroundedAction()) != null && !guessedActionWithLessQValue(curState, guessedAction)) {
                    lastSelectedAction = guessedAction.getGroundedAction();
                    lastSelectedActionCopy = new SelfManagementSimpleGroundedAction(guessedAction);
                    ((SelfManagementSimpleGroundedAction) lastSelectedActionCopy).setSelectedBy(SelfManagementAction.SelectedBy.STATE_CLASSIFIER);
                }
            }
        }

        return lastSelectedAction;
    }

    private boolean guessedActionWithLessQValue(HashableState curState, Action guessedAction) {
        if(this.getQ(curState, guessedAction.getGroundedAction()).q < this.getMaxQ(curState)) {
            return true;
        }
        return false;
    }

    public void executeLearningStep(Environment env, HashableState curState, JsEpisodeAnalysis ea) {
        QValue curQ = this.getQ(curState, lastSelectedAction);
        List<QValue> currentQVals = copyCurrentQVals(this.qIndex.get(curState).qEntry);

        EnvironmentOutcome eo = lastSelectedAction.executeIn(env);


        HashableState nextState = this.stateHash(eo.op);
        double maxQ = 0.;

        if (!eo.terminated) {
            maxQ = this.getMaxQ(nextState);
        }

        //manage option specifics
        double r = eo.r;
        double discount = eo instanceof EnvironmentOptionOutcome ? ((EnvironmentOptionOutcome) eo).discount : this.gamma;
        int stepInc = eo instanceof EnvironmentOptionOutcome ? ((EnvironmentOptionOutcome) eo).numSteps : 1;
        eStepCounter += stepInc;

        if (lastSelectedAction.action.isPrimitive() || !this.shouldAnnotateOptions) {
            JsEnvironmentOutcome eeo = (JsEnvironmentOutcome) eo;
            ea.recordTransitionTo(lastSelectedActionCopy, nextState.s, r, currentQVals, eeo);
        } else {
            ea.appendAndMergeEpisodeAnalysis(((Option) lastSelectedAction.action).getLastExecutionResults());
        }

        double oldQ = curQ.q;

        //update Q-value
        curQ.q = curQ.q + this.learningRate.pollLearningRate(this.totalNumberOfSteps, curState.s, lastSelectedAction) * (r + (discount * maxQ) - curQ.q);

        double deltaQ = Math.abs(oldQ - curQ.q);
        if (deltaQ > maxQChangeInLastEpisode) {
            maxQChangeInLastEpisode = deltaQ;
        }

        //move on polling environment for its current state in case it changed during processing
        curState = this.stateHash(env.getCurrentObservation());
        this.totalNumberOfSteps++;
    }

    protected List<QValue> copyCurrentQVals(List<QValue> qValues) {
        List<QValue> copyList = new ArrayList<>();
        for (QValue qv : qValues) {
            copyList.add(new QValue(qv));
        }
        return copyList;
    }
}
