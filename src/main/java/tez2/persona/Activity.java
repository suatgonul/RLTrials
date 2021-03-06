package tez2.persona;

import org.joda.time.DateTime;
import tez2.environment.context.Context;

/**
 * Created by suatgonul on 12/2/2016
 * Represent an activity that is performed by a person during the day. Activities having long duration might have
 * intermediate phone checks with varying durations.
 */
public class Activity {
    private String name;

    private DateTime start;

    // duration of activity in minutes
    private int duration;

    private Context context = new Context();

    private boolean suitableForBehavior;

    public Activity() {

    }

    public Activity(String name, DateTime start, int duration, Context context) {
        this.name = name;
        this.start = start;
        this.duration = duration;
        this.context = context.copy();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStart(DateTime start) {
        this.start = start;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getName() {
        return name;
    }

    public DateTime getStart() {
        return start;
    }

    public int getDuration() {
        return duration;
    }

    public Context getContext() {
        return context;
    }

    public DateTime getEndTime() {
        return start.plusMinutes(duration);
    }

    public boolean isSuitableForBehavior() {
        return suitableForBehavior;
    }

    public void setSuitableForBehavior(boolean suitableForBehavior) {
        this.suitableForBehavior = suitableForBehavior;
    }

    public Activity copy() {
        Activity activity = new Activity(getName(), getStart(), getDuration(), getContext());
        return activity;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name)
                .append(", Start: ").append(start)
                .append(", Duration: ").append(duration)
                .append(", Suitable: ").append(suitableForBehavior)
                .append(", Context: ").append(context.toString());
        return sb.toString();
    }
}
