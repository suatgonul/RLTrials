package tez2.domain;

import burlap.oomdp.core.objects.ObjectInstance;
import burlap.oomdp.core.states.State;
import burlap.oomdp.singleagent.Action;
import tez2.environment.context.*;

import java.util.List;

import static tez2.domain.DomainConfig.*;

/**
 * Created by suat on 14-May-17.
 */
public class SelfManagementState {
    public static StringBuilder transformToCSV(State s) {
        return transformToCSV(s, null);
    }

    public static StringBuilder transformToCSV(State s, String a) {
        ObjectInstance o = s.getObjectsOfClass(CLASS_STATE).get(0);
        String time = o.getStringValForAttribute(ATT_ACTIVITY_TIME);
        int hour = Integer.valueOf(time.split(":")[0]);
        int minute = Integer.valueOf(time.split(":")[1]);
        DayType dayType = DayType.values()[o.getIntValForAttribute(ATT_DAY_TYPE)];
        Location location = Location.values()[o.getIntValForAttribute(ATT_LOCATION)];
        PhysicalActivity activity = PhysicalActivity.values()[o.getIntValForAttribute(ATT_ACTIVITY)];
        PhoneUsage phoneUsage = PhoneUsage.APPS_ACTIVE.values()[o.getIntValForAttribute(ATT_PHONE_USAGE)];
        EmotionalStatus emotionalStatus = EmotionalStatus.values()[o.getIntValForAttribute(ATT_EMOTIONAL_STATUS)];

        StringBuilder sb = new StringBuilder();
        sb.append(hour * 60 + minute).append(",")
                .append(dayType).append(",")
                .append(location).append(",")
                .append(activity).append(",")
                .append(phoneUsage).append(",")
                .append(emotionalStatus);
        if (a != null) {
            sb.append(",")
                    .append(a);
        }
        sb.append("\n");
        return sb;
    }
}
