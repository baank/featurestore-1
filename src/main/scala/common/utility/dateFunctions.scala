package common.utility

import java.text.SimpleDateFormat
import java.util.Date

/**
  * Created by markmo on 27/02/2016.
  */
object dateFunctions {

  val OUTPUT_DATE_PATTERN = "yyyy-MM-dd"

  val OUTPUT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

  /**
    * Converts a date string of a given pattern to a Date object.
    *
    * @param str String date to convert
    * @param pattern String format of date string to parse
    * @return Date
    */
  def convertStringToDate(str: String, pattern: String) = {
    val format = new SimpleDateFormat(pattern)
    format.parse(str)
  }

  /**
    * Formats a date string of a given pattern to a conformed format (yyyy-MM-dd).
    *
    * @param str String date to format
    * @param pattern String format of date string to parse
    * @return String formatted date (yyyy-MM-dd)
    */
  def formatDateString(str: String, pattern: String) = {
    val dt = convertStringToDate(str, pattern)
    val outputFormat = new SimpleDateFormat(OUTPUT_DATE_PATTERN)
    outputFormat.format(dt)
  }

  /**
    * Formats a date string of a given pattern to a conformed date and time format (yyyy-MM-dd HH:mm:ss).
    *
    * @param str String date to format
    * @param pattern String format of date string to parse
    * @return String formatted date and time (yyyy-MM-dd HH:mm:ss)
    */
  def formatDateTimeString(str: String, pattern: String) = {
    val dt = convertStringToDate(str, pattern)
    val outputFormat = new SimpleDateFormat(OUTPUT_DATE_TIME_PATTERN)
    outputFormat.format(dt)
  }

  /**
    * Converts a date string of a given pattern to epoch (unix) time.
    *
    * Defined as the number of seconds that have elapsed since 00:00:00 Coordinated Universal Time (UTC), Thursday, 1 January 1970, not counting leap seconds.
    *
    * @param str String date to parse
    * @param pattern String format of date string to parse
    * @return long epoch (unix) time
    */
  def convertStringToTimestamp(str: String, pattern: String) = {
    convertStringToDate(str, pattern).getTime
  }

  implicit class RichDate(val date: Date) extends AnyVal {

    def <=(when: Date): Boolean = date.before(when) || date.equals(when)

    def >=(when: Date): Boolean = date.after(when) || date.equals(when)

  }

}