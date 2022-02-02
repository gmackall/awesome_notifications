package me.carda.awesome_notifications.awesome_notifications_core.models;

import android.content.Context;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import me.carda.awesome_notifications.awesome_notifications_core.Definitions;
import me.carda.awesome_notifications.awesome_notifications_core.externalLibs.CronExpression;
import me.carda.awesome_notifications.awesome_notifications_core.exceptions.AwesomeNotificationsException;
import me.carda.awesome_notifications.awesome_notifications_core.utils.CronUtils;
import me.carda.awesome_notifications.awesome_notifications_core.utils.DateUtils;
import me.carda.awesome_notifications.awesome_notifications_core.utils.ListUtils;
import me.carda.awesome_notifications.awesome_notifications_core.utils.StringUtils;

public class NotificationCrontabModel extends NotificationScheduleModel {

    public String initialDateTime;
    public String expirationDateTime;
    public String crontabExpression;
    public List<String> preciseSchedules;

    @Override
    @SuppressWarnings("unchecked")
    public NotificationCrontabModel fromMap(Map<String, Object> arguments) {
        super.fromMap(arguments);

        initialDateTime = getValueOrDefault(arguments, Definitions.NOTIFICATION_INITIAL_DATE_TIME, String.class);
        expirationDateTime = getValueOrDefault(arguments, Definitions.NOTIFICATION_EXPIRATION_DATE_TIME, String.class);
        crontabExpression = getValueOrDefault(arguments, Definitions.NOTIFICATION_CRONTAB_EXPRESSION, String.class);
        preciseSchedules = getValueOrDefault(arguments, Definitions.NOTIFICATION_PRECISE_SCHEDULES, List.class);

        return this;
    }

    @Override
    public Map<String, Object> toMap(){
        Map<String, Object> returnedObject = super.toMap();

        returnedObject.put(Definitions.NOTIFICATION_INITIAL_DATE_TIME, initialDateTime);
        returnedObject.put(Definitions.NOTIFICATION_EXPIRATION_DATE_TIME, expirationDateTime);
        returnedObject.put(Definitions.NOTIFICATION_CRONTAB_EXPRESSION, crontabExpression);
        returnedObject.put(Definitions.NOTIFICATION_PRECISE_SCHEDULES, preciseSchedules);

        return returnedObject;
    }

    @Override
    public String toJson() {
        return templateToJson();
    }

    @Override
    public NotificationCalendarModel fromJson(String json){
        return (NotificationCalendarModel) super.templateFromJson(json);
    }

    @Override
    public void validate(Context context) throws AwesomeNotificationsException {

        if(
            StringUtils.isNullOrEmpty(initialDateTime) &&
            StringUtils.isNullOrEmpty(crontabExpression) &&
            ListUtils.isNullOrEmpty(preciseSchedules)
        )
            throw new AwesomeNotificationsException("At least one schedule parameter is required");

        TimeZone timeZone = StringUtils.isNullOrEmpty(this.timeZone) ?
                DateUtils.getLocalTimeZone() :
                TimeZone.getTimeZone(this.timeZone);

        if (timeZone == null)
            throw new AwesomeNotificationsException("Invalid time zone");

        try {
            Date initialDate = null, expirationDate = null, preciseDate  = null;

            if(initialDateTime != null || expirationDateTime != null){
                if(initialDateTime != null){
                    initialDate = DateUtils.stringToDate(initialDateTime, this.timeZone);
                    if(initialDate == null)
                        throw new AwesomeNotificationsException("Schedule initial date is invalid");
                }

                if(expirationDateTime != null){
                    expirationDate = DateUtils.stringToDate(expirationDateTime, this.timeZone);
                    if(expirationDate == null)
                        throw new AwesomeNotificationsException("Schedule expiration date is invalid");
                }

                if(initialDate != null && expirationDate != null && !expirationDate.after(initialDate))
                    throw new AwesomeNotificationsException("Expiration date must be greater than initial date");
            }

            if(crontabExpression != null && !CronExpression.isValidExpression(crontabExpression))
                throw new AwesomeNotificationsException("Schedule cron expression is invalid");

            if(preciseSchedules != null){
                for(String preciseSchedule : preciseSchedules){
                    preciseDate = DateUtils.stringToDate(preciseSchedule, timeZone.toString());
                    if(preciseDate == null){
                        throw new AwesomeNotificationsException("Precise date '"+preciseSchedule+"' is invalid");
                    }
                }
            }

        } catch (AwesomeNotificationsException e){
            throw e;
        } catch (Exception e){
            throw new AwesomeNotificationsException("Schedule time is invalid");
        }
    }

    @Override
    public Calendar getNextValidDate(Date fixedNowDate) throws AwesomeNotificationsException {

        try {

            TimeZone timeZone = StringUtils.isNullOrEmpty(this.timeZone) ?
                    DateUtils.getLocalTimeZone() :
                    TimeZone.getTimeZone(this.timeZone);

            if (timeZone == null)
                throw new AwesomeNotificationsException("Invalid time zone");

            if (fixedNowDate == null)
                fixedNowDate = DateUtils.getLocalDateTime(this.timeZone);

            if(!StringUtils.isNullOrEmpty(expirationDateTime)){
                Date expirationDate = DateUtils.stringToDate(expirationDateTime, this.timeZone);
                if(fixedNowDate.after(expirationDate)){
                    return null;
                }
            }

            Date initialDate = null;
            if(!StringUtils.isNullOrEmpty(initialDateTime)){
                initialDate = DateUtils.stringToDate(initialDateTime, this.timeZone);
            }

            Calendar preciseCalendar = null, crontabCalendar = null;

            if (!ListUtils.isNullOrEmpty(preciseSchedules)){
                Date earlierDate = null;

                for (String preciseSchedule : preciseSchedules) {
                    Date preciseDate = DateUtils.stringToDate(preciseSchedule, this.timeZone);

                    if(initialDate != null && preciseDate.before(initialDate))
                        continue;

                    if(preciseDate.before(fixedNowDate))
                        continue;

                    if(earlierDate == null){
                        earlierDate = preciseDate;
                    }
                    else{
                        if(earlierDate.after(preciseDate)){
                            earlierDate = preciseDate;
                        }
                    }
                }

                if(earlierDate != null){
                    preciseCalendar = createCalendarFromDate(earlierDate, timeZone);
                }
            }

            if(!StringUtils.isNullOrEmpty(crontabExpression))
                crontabCalendar = CronUtils.getNextCalendar( initialDateTime, crontabExpression, fixedNowDate, timeZone );

            if (preciseCalendar == null)
                return crontabCalendar;

            if (crontabCalendar == null)
                return preciseCalendar;

            if (preciseCalendar.before(crontabCalendar))
                return preciseCalendar;

            return crontabCalendar;

        } catch (AwesomeNotificationsException e){
            throw e;
        } catch (Exception e){
            throw new AwesomeNotificationsException("Schedule time is invalid");
        }
    }

    private Calendar createCalendarFromDate(Date date, TimeZone timeZone){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(timeZone);
        calendar.setTime(date);
        return calendar;
    }
}