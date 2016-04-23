package redditheadlines;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.ui.*;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;


public class RedditHeadlinesSpeechlet implements Speechlet {

    // TODO: request post type [top, recent, controversial, etc]

    private static final Logger LOGGER = LoggerFactory.getLogger(RedditHeadlinesSpeechlet.class);

    // reddit url params
    private static final String URL_PREFIX = "https://www.reddit.com/r/";
    private static final String URL_LIMIT = "/top.json?limit=";
    private static final String TEST_URL = "https://www.reddit.com/r/news/top.json?limit=3";

    private static final int INITIAL_READ_COUNT = 3;
    private static final int INITIAL_FETCH_COUNT = 9;

    // skill name
    private static final String SKILL_NAME = "Reddit Headlines";

    // session vars
    private static final String KEY_SESSION_LAST_INDEX = "index";
    private static final String KEY_SESSION_TEXT = "text";

    // slots
    private static final String SLOT_SUB_REDDIT = "subreddit";

    // intent types
    private static final String INTENT_GET_SUB_REDDIT_INTENT = "GetSubRedditIntent";
    private static final String INTENT_GET_NEXT_POSTS_INTENT = "GetNextPostsIntent";
    private static final String INTENT_AMAZON_HELP = "AMAZON.HelpIntent";
    private static final String INTENT_AMAZON_STOP = "AMAZON.StopIntent";
    private static final String INTENT_AMAZON_CANCEL = "AMAZON.CancelIntent";

    // response strings
    private static final String RESPONSE_WELCOME = "Welcome to Reddit Headlines, you can say a news sub reddit name or help";
    private static final String RESPONSE_STOP = "Bye bye";
    private static final String RESPONSE_CANCEL = "Come again soon";
    private static final String RESPONSE_HELP_SPEECH = "With Reddit Headlines, you can get"
            + " the recent posts for popular sub reddits."
            + " For example, you could say top posts in news, worldnews or politics."
            + " Now, which sub reddit do you want?";
    private static final String RESPONSE_HELP_TEXT = "Which sub reddit do you want?";
    private static final String RESPONSE_MORE_POSTS = " Do you want to hear more posts";


    @Override
    public void onSessionStarted(SessionStartedRequest request, Session session) throws SpeechletException {
        LOGGER.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(LaunchRequest request, Session session) throws SpeechletException {
        LOGGER.info("onLaunch requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
        return getWelcomeResponse();
    }

    @Override
    public SpeechletResponse onIntent(IntentRequest request, Session session) throws SpeechletException {
        LOGGER.info("onIntent requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());

        Intent intent = request.getIntent();
        final String intentName = (intent != null) ? intent.getName() : null;

        if (INTENT_GET_SUB_REDDIT_INTENT.equals(intentName)) {
            return handleSubRedditEventRequest(intent, session);
        } else if (INTENT_GET_NEXT_POSTS_INTENT.equals(intentName)) {
            return handleNextPostsEventRequest(intent, session);
        } else if (INTENT_AMAZON_STOP.equals(intentName)) {
            return getSimpleTellResponse(RESPONSE_STOP);
        } else if (INTENT_AMAZON_CANCEL.equals(intentName)) {
            return getSimpleTellResponse(RESPONSE_CANCEL);
        } else if (INTENT_AMAZON_HELP.equals(intentName)) {
            return newAskResponse(RESPONSE_HELP_SPEECH, false, RESPONSE_HELP_TEXT, false);
        }

        throw new SpeechletException("Invalid Intent");
    }

    @Override
    public void onSessionEnded(SessionEndedRequest request, Session session) throws SpeechletException {
        LOGGER.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(), session.getSessionId());
        // any cleanup logic goes here
    }

    /**
     * Welcome response when invoked without subreddit params
     *
     * @return
     */
    private SpeechletResponse getWelcomeResponse() {
        final String speechText = RESPONSE_WELCOME;

        // create card content
        SimpleCard card = new SimpleCard();
        card.setTitle(SKILL_NAME);
        card.setContent(speechText);

        // create plain text output
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // create reprompt
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(speech);

        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    /**
     * Response when stopped
     *
     * @return
     */
    private SpeechletResponse getSimpleTellResponse(final String text) {
        PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
        outputSpeech.setText(text);
        return SpeechletResponse.newTellResponse(outputSpeech);
    }

    /**
     * Get subreddit as requested
     *
     * @param intent
     * @param session
     * @return
     */
    private SpeechletResponse handleSubRedditEventRequest(Intent intent, Session session) {

        Slot subredditSlot = intent.getSlot(SLOT_SUB_REDDIT);
        String subredditName = "";
        if (subredditSlot == null || subredditSlot.getValue() == null) {
            newAskResponse("Unknown sub reddit. Which sub reddit do you want?", false, "Which sub reddit do you want?", false);
        } else {
            subredditName = Utils.stripPrefix(subredditSlot.getValue());
        }

        String speechPrefixContent = "<p>Reddit headlines for are " + subredditName + "</p> ";
        String cardPrefixContent = "Reddit Headlines for r/" + subredditName + ", ";
        String cardTitle = "Reddit Headlines for r/" + subredditName;

        ArrayList<String> headlines = getJsonTitlesFromReddit(subredditName);
        String speechOutput;
        if (headlines.isEmpty()) {
            speechOutput = "There is a problem connecting to reddit are " + subredditName + ". Please try again later.";

            // Create the plain text output
            SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
            outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");

            return SpeechletResponse.newTellResponse(outputSpeech);
        }

        StringBuilder speechOutputBuilder = new StringBuilder();
        speechOutputBuilder.append(speechPrefixContent);

        StringBuilder cardOutputBuilder = new StringBuilder();
        cardOutputBuilder.append(cardPrefixContent);

        String headline;
        String articleNumber;
        for (int i = 0; i < headlines.size(); i++) {
            headline = headlines.get(i);
            articleNumber = String.valueOf(i + 1);

            speechOutputBuilder.append("<p>");
            speechOutputBuilder.append("Headline ");
            speechOutputBuilder.append(articleNumber);
            speechOutputBuilder.append("</p><p>");
            speechOutputBuilder.append(headline);
            speechOutputBuilder.append("</p> ");

            cardOutputBuilder.append(headline);
            cardOutputBuilder.append("\n");
        }

        speechOutputBuilder.append(RESPONSE_MORE_POSTS);
        cardOutputBuilder.append(RESPONSE_MORE_POSTS);
        speechOutput = speechOutputBuilder.toString();

        final String repromptText = RESPONSE_HELP_SPEECH;

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle(cardTitle);
        card.setContent(cardOutputBuilder.toString());

        // After reading the first 3 events, set the count to 3 and add the events
        // to the session attributes
        session.setAttribute(KEY_SESSION_LAST_INDEX, INITIAL_READ_COUNT);
        session.setAttribute(KEY_SESSION_TEXT, headlines);

        SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
        response.setCard(card);
        return response;
    }

    private SpeechletResponse handleNextPostsEventRequest(Intent intent, Session session) {

        String cardTitle = "More Reddit Headlines";

        ArrayList<String> headlines = (ArrayList<String>) session.getAttribute(KEY_SESSION_TEXT);

        int index = (Integer) session.getAttribute(KEY_SESSION_LAST_INDEX);
        String speechOutput = "";
        String cardOutput = "";
        if (headlines == null) {
            speechOutput = RESPONSE_HELP_SPEECH;
        } else if (index >= headlines.size()) {
            speechOutput =
                    "There are no more headlines for this sub reddit. Try another sub reddit by saying, "
                            + " get posts for news, world news, or politics.";
        } else {
            StringBuilder speechOutputBuilder = new StringBuilder();
            StringBuilder cardOutputBuilder = new StringBuilder();
            for (int i = 0; i < INITIAL_FETCH_COUNT && index < headlines.size(); i++) {
                speechOutputBuilder.append("<p>");
                speechOutputBuilder.append(headlines.get(index));
                speechOutputBuilder.append("</p> ");
                cardOutputBuilder.append(headlines.get(index));
                cardOutputBuilder.append(" ");
                index++;
            }
            if (index < headlines.size()) {
                speechOutputBuilder.append(RESPONSE_MORE_POSTS);
                cardOutputBuilder.append(RESPONSE_MORE_POSTS);
            }
            session.setAttribute(KEY_SESSION_LAST_INDEX, index);
            speechOutput = speechOutputBuilder.toString();
            cardOutput = cardOutputBuilder.toString();
        }
        String repromptText = "Do you want more posts?";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle(cardTitle);
        card.setContent(cardOutput.toString());

        SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
        response.setCard(card);
        return response;
    }

    /**
     * get JSON for subreddit
     *
     * @param subredditName
     * @return
     */
    private ArrayList<String> getJsonTitlesFromReddit(String subredditName) {
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        String text = "";
        try {
            String line;
            URL url = new URL(URL_PREFIX + subredditName + URL_LIMIT + INITIAL_READ_COUNT);
            inputStream = new InputStreamReader(url.openStream(), Charset.forName("UTF-8"));
            bufferedReader = new BufferedReader(inputStream);
            StringBuilder builder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            text = builder.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            text = "";
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(bufferedReader);
        }

        return parseJson(text);
    }

    /**
     * Parse titles from JSON
     *
     * @param text
     * @return
     */
    private ArrayList<String> parseJson(String text) {

        ArrayList<String> headlines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return headlines;
        }

        JSONArray children = null;
        try {
            children = (new JSONObject(text)).getJSONObject("data").getJSONArray("children");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (children == null) {
            return headlines;
        }

        String title;
        for (int i = 0; i < children.length(); i++) {
            try {
                title = children
                        .getJSONObject(i)
                        .getJSONObject("data")
                        .getString("title");
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }
            headlines.add(title);
        }
        return headlines;
    }

    /**
     * Helper for new ask responses
     *
     * @param stringOutput
     * @param isOutputSsml
     * @param repromptText
     * @param isRepromptSsml
     * @return
     */
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml, String repromptText, boolean isRepromptSsml) {

        OutputSpeech outputSpeech, repromptOutputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }

        if (isRepromptSsml) {
            repromptOutputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
        } else {
            repromptOutputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
        }

        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);
        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

}
