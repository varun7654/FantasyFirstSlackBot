package com.dacubeking.fantasyfirst;

public class Screens {
    public static final String CREATE_EVENT_CALLBACK_ID = "createEventButton";
    public static final String CREATE_EVENT = """
            {
             	"type": "modal",
             	"title": {
             		"type": "plain_text",
             		"text": "Create an Event",
             		"emoji": true
             	},
             	"submit": {
             		"type": "plain_text",
             		"text": "Submit",
             		"emoji": true
             	},
             	"close": {
             		"type": "plain_text",
             		"text": "Cancel",
             		"emoji": true
             	},
             	"callback_id": "%s",
             	"blocks": [
             		{
             			"type": "input",
             			"label": {
             				"type": "plain_text",
             				"text": "Enter the Event Name",
             				"emoji": true
             			},
             			"element": {
             				"type": "plain_text_input",
             				"multiline": false,
             				"action_id": "event_name"
             			}
             		},
             		{
             			"type": "input",
             			"label": {
             				"type": "plain_text",
             				"text": "List out a comma seperated list of teams (e.g. 254,1678,7157)",
             				"emoji": true
             			},
             			"element": {
             				"type": "plain_text_input",
             				"multiline": true,
             				"action_id": "teams"
             			}
             		},
             		{
             			"type": "input",
             			"label": {
             				"type": "plain_text",
             				"text": "Select the channel to use",
             				"emoji": true
             			},
             			"element": {
             				"type": "channels_select",
             				"action_id": "channel"
             			}
             		},
             		{
             			"type": "input",
             			"label": {
             				"type": "plain_text",
             				"text": "Numbers of teams per alliance",
             				"emoji": true
             			},
             			"element": {
             				"type": "number_input",
             				"is_decimal_allowed": false,
             				"action_id": "teams_per_alliance"
             			}
             		}
             	]
             }
            """.formatted(CREATE_EVENT_CALLBACK_ID);
}
