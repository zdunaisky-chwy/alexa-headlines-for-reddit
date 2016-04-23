package redditheadlines;

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;

import java.util.HashSet;
import java.util.Set;


public class RedditHeadlinesSpeechletRequestHandler extends SpeechletRequestStreamHandler {

    private static final Set<String> supportedApplicationIds = new HashSet<>();

    static {

        /*
         * This Id can be found on https://developer.amazon.com/edw/home.html#/ "Edit" the relevant
         * Alexa Skill and put the relevant Application Ids in this Set.
         */
        supportedApplicationIds.add("amzn1.echo-sdk-ams.app.50ced2af-fc95-4b3c-b1a9-60334a9bd842");
    }

    public RedditHeadlinesSpeechletRequestHandler() {
        super(new RedditHeadlinesSpeechlet(), supportedApplicationIds);
    }
}
