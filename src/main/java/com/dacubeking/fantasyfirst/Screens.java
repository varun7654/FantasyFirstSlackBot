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
             				"text": "Enter a TBA event code, or list out a comma seperated list of teams (e.g. 254,1678,7157)",
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

    public static final String PICK_TEAM_CALLBACK_ID = "team_pick_number";

    public static final String PICK_A_TEAM = """
            {
            	"type": "modal",
            	"title": {
            		"type": "plain_text",
            		"text": "Pick A Team",
            		"emoji": true
            	},
            	"submit": {
            		"type": "plain_text",
            		"text": "Submit Pick",
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
            			"type": "divider"
            		},
            		{
            			"type": "section",
            			"block_id": "sectionBlockOnlyMrkdwn",
            			"text": {
            				"type": "mrkdwn",
            				"text": "*It's %s's turn to pick!*"
            			}
            		},
            		{
            			"type": "divider"
            		},
            		{
            			"type": "context",
            			"elements": [
            				{
            					"type": "mrkdwn",
            					"text": "*Available Teams:*"
            				}
            			]
            		},
            		{
            			"type": "context",
            			"elements": [
            				{
            					"type": "plain_text",
            					"text": "%s",
            					"emoji": true
            				}
            			]
            		},
            		{
            			"type": "input",
            			"element": {
            				"type": "number_input",
            				"is_decimal_allowed": false,
            				"action_id": "team_pick_number"
            			},
            			"label": {
            				"type": "plain_text",
            				"text": "Enter your pick (e.g 254)",
            				"emoji": true
            			}
            		},
            		{
            			"type": "section",
            			"block_id": "sectionBlockOnlyPlainText",
            			"text": {
            				"type": "plain_text",
            				"text": "If you're not able to submit this form check that you've entered a valid team & that it's your turn to pick",
            				"emoji": true
            			}
            		}
            	]
            }
            """; // .formatted(player.slackId(), teamsString);
}
