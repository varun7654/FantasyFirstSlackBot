package com.dacubeking.fantasyfirst;

import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewClose;
import com.slack.api.model.view.ViewSubmit;
import com.slack.api.model.view.ViewTitle;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.*;

public class Screens {
    public static final String CREATE_EVENT_CALLBACK_ID = "createEventButton";
    public static final String CREATE_EVENT = """
            {
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
            	"type": "modal",
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
            		},
            		{
            			"type": "input",
            			"label": {
            				"type": "plain_text",
            				"text": "Target Player Count Per Game",
            				"emoji": true
            			},
            			"element": {
            				"type": "number_input",
            				"is_decimal_allowed": false,
            				"action_id": "target_player_count_per_game"
            			}
            		},
            		{
            			"type": "context",
            			"elements": [
            				{
            					"type": "mrkdwn",
            					"text": "When the game is started, players will be split into separate drafts with a target number of players per draft being the above. Setting `0` above will set the target game size based on the number of available teams to pick."
            				}
            			]
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


    public static View buildPickATeamErrorView(String callbackId, String playerSlackId, String teamsString, String errorMessage) {
        return View.builder()
                .type("modal")
                .callbackId(callbackId)
                .title(ViewTitle.builder().text("Pick A Team").emoji(true).build())
                .submit(ViewSubmit.builder().text("Submit Pick").emoji(true).build())
                .close(ViewClose.builder().text("Cancel").emoji(true).build())
                .blocks(asBlocks(
                        divider(),
                        section(section -> section
                                .blockId("sectionBlockOnlyMrkdwn")
                                .text(markdownText("*It's " + playerSlackId + "'s turn to pick!*"))
                        ),
                        divider(),
                        context(ctx -> ctx
                                .elements(asContextElements(
                                        markdownText("*Available Teams:*")
                                ))
                        ),
                        context(ctx -> ctx
                                .elements(asContextElements(
                                        plainText(pt -> pt.text(teamsString).emoji(true))
                                ))
                        ),
                        input(input -> input
                                .label(plainText(pt -> pt.text("Enter your pick (e.g 254)").emoji(true)))
                                .element(numberInput(inputElement -> inputElement
                                        .actionId("team_pick_number").
                                        decimalAllowed(false)
                                ))
                        ),
                        context(ctx -> ctx
                                .elements(asContextElements(
                                        imageElement(img -> img
                                                .imageUrl("https://api.slack.com/img/blocks/bkb_template_images/notificationsWarningIcon.png")
                                                .altText("notifications warning icon")
                                        ),
                                        markdownText("*" + errorMessage + "*")
                                ))
                        ),
                        section(section -> section
                                .blockId("sectionBlockOnlyPlainText")
                                .text(plainText(pt -> pt
                                        .text("If you're not able to submit this form check that you've entered a valid team & that it's your turn to pick")
                                        .emoji(true)
                                ))
                        )
                ))
                .build();
    }
}
