package power2dm.model;

import burlap.behavior.policy.Policy;
import burlap.behavior.singleagent.EpisodeAnalysis;
import burlap.behavior.singleagent.learning.tdmethods.QLearning;
import burlap.behavior.singleagent.learning.tdmethods.QLearningStateNode;
import burlap.behavior.valuefunction.ValueFunctionInitialization;
import burlap.oomdp.core.Domain;
import burlap.oomdp.singleagent.environment.Environment;
import burlap.oomdp.statehashing.HashableState;
import burlap.oomdp.statehashing.HashableStateFactory;
import power2dm.reporting.EpisodeAnalyser;

import java.util.Map;


/**
 * * This is an extension for the base {@link QLearning} class for only debugging purposes. In this extension, we just
 * call an {@link EpisodeAnalyser} after the #runLearningEpisode execution in order to print the information about the
 * completed episode.
 * <p/>
 * Created by suat on 15-Apr-16.
 */
public class P2DMQLearning extends QLearning {

    public P2DMQLearning(Domain domain, double gamma, HashableStateFactory hashingFactory, double qInit, double learningRate, EpisodeAnalyser episodeAnalyser) {
        //şu constructor'u adam edip, sarsa lambdayı deneyecem, o belki saat 21'de gösterme işini çözer
        Domain domain, double gamma, HashableStateFactory hashingFactory, ValueFunctionInitialization qInitFunction, double learningRate, Policy learningPolicy, int maxEpisodeSize

        super(domain, gamma, hashingFactory, qInit, learningRate);
        this.episodeAnalyser = episodeAnalyser;
        this.episodeAnalyser.setLearningAlgorithm(this);
    }

    private EpisodeAnalyser episodeAnalyser;

    public EpisodeAnalysis runLearningEpisode(Environment env, int maxSteps, int episodeNo) {
        EpisodeAnalysis ea = super.runLearningEpisode(env, maxSteps);
        episodeAnalyser.printQValuesForPreferredRange(ea, episodeNo);
        episodeAnalyser.populateMaxQValues(ea);
        return ea;
    }

    public Map<HashableState, QLearningStateNode> getAllQValues() {
        return qIndex;
    }

    public Policy getPolicy() {
        return learningPolicy;
    }
}
