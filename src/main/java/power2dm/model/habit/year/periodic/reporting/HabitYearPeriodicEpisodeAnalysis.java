package power2dm.model.habit.year.periodic.reporting;

import power2dm.model.TaskDifficulty;
import power2dm.reporting.P2DMEpisodeAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by suat on 16-May-16.
 */
public class HabitYearPeriodicEpisodeAnalysis extends P2DMEpisodeAnalysis {
    private int episodeNo;
    private List<Integer> habitGain = new ArrayList<Integer>();
    private List<Boolean> calorieIntakeEntry = new ArrayList<Boolean>();
    private List<Integer> generatedRandom = new ArrayList<Integer>();
    private TaskDifficulty difficulty;

    public HabitYearPeriodicEpisodeAnalysis(int episodeNo, TaskDifficulty difficulty) {
        super(episodeNo);
        this.difficulty = difficulty;
    }

    public HabitYearPeriodicEpisodeAnalysis(P2DMEpisodeAnalysis ea) {
        super(ea);
        this.totalReward = ea.getTotalReward();
        this.episodeNo = ea.getEpisodeNo();
    }

    public int getEpisodeNo() {
        return episodeNo;
    }

    public void addHabitGainEntry(int habitGain) {
        this.habitGain.add(habitGain);
    }

    public List<Integer> getHabitGainList() {
        return habitGain;
    }

    public void addCalorieIntakeEntry(boolean calorieIntakeEntry) {
        this.calorieIntakeEntry.add(calorieIntakeEntry);
    }

    public List<Boolean> getCalorieIntakeList() {
        return calorieIntakeEntry;
    }

    public void addGeneratedRandom(int randomInt) {
        generatedRandom.add(randomInt);
    }

    public List<Integer> getGeneratedRandomList() {
        return generatedRandom;
    }

    public TaskDifficulty getTaskDifficulty() {
        return difficulty;
    }
}
