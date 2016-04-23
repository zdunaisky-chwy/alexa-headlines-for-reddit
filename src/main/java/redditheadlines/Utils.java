package redditheadlines;


public class Utils {

    /**
     * Subreddit may be prepended with "are" if the user requests a subreddit verbally as "r/subname"
     *
     * @param text
     * @return
     */
    public static String stripPrefix(String text) {
        if ((text != null && !text.contains("are")) || text == null) {
            return text;
        }
        if (text.length() > 2 && text.substring(0, 2).equalsIgnoreCase("are")) {
            return text.substring(3, text.length() - 1);
        }
        return text;
    }

}
