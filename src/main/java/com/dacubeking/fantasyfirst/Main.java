package com.dacubeking.fantasyfirst;

import com.dacubeking.fantasyfirst.game.Game;
import com.dacubeking.fantasyfirst.game.Game.Player;
import com.dacubeking.fantasyfirst.game.Game.Team;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.bolt.service.InstallationService;
import com.slack.api.bolt.service.OAuthStateService;
import com.slack.api.bolt.service.builtin.ClientOnlyOAuthStateService;
import com.slack.api.bolt.service.builtin.FileInstallationService;
import com.slack.api.bolt.service.builtin.FileOAuthStateService;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.SlackApiResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.ButtonElement;
import com.slack.api.model.event.AppHomeOpenedEvent;
import com.slack.api.model.view.ViewState.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.view.Views.view;

public class Main {
    public static final ButtonElement createEventButton = new ButtonElement();
    public static final String DATA_DIR = "./data";

    static {
        createEventButton.setText(new PlainTextObject("Create Event", true));
        createEventButton.setActionId("createEventButton");
    }

    public static final ButtonElement pickTeamButton = new ButtonElement();

    static {
        pickTeamButton.setText(new PlainTextObject("Pick A Team", true));
        pickTeamButton.setActionId("teaam_number_pick");
    }

    public static final ConcurrentMap<String, ConcurrentMap<UUID, Game>> games = new ConcurrentHashMap<>(); // <workspaceId, <gameId, game>>

    static final Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] args) throws Exception {
        try {
            FileInputStream fis = new FileInputStream("games.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object object = ois.readObject();
            var gamesRead = (ConcurrentMap<String, ConcurrentMap<UUID, Game>>) object;
            games.putAll(gamesRead);
        } catch (Exception e) {
            System.out.println("No save file found");
            e.printStackTrace();
        }

        GetTeamsAtEvent getTeams = new GetTeamsAtEvent(Creds.TBA_API_KEY);

        var appConfig = new AppConfig();
        new File(DATA_DIR).mkdirs();
        InstallationService installationService = new FileInstallationService(appConfig, DATA_DIR);
        installationService.setHistoricalDataEnabled(true);

        appConfig.setSigningSecret(Creds.SLACK_SIGNING_KEY);
//        appConfig.setClientId(Creds.SLACK_CLIENT_ID);
//        appConfig.setClientSecret(Creds.SLACK_CLIENT_SECRET);
        appConfig.setSingleTeamBotToken(Creds.SLACK_BOT_TOKEN);
//        appConfig.setScope("chat:write,chat:write.public,users:read,users.profile:read");
//        appConfig.setOauthCompletionUrl("https://fantasyfirst.dacubeking.com/slack/oauth/completion");
//        appConfig.setOauthCancellationUrl("https://fantasyfirst.dacubeking.com/slack/oauth/cancellation");

        var app = new App(appConfig);
        //app.service(installationService);

        App oauthApp = new App(appConfig).asOAuthApp(true);
        oauthApp.service(installationService);

        OAuthStateService stateService = new FileOAuthStateService(appConfig, DATA_DIR);
        oauthApp.service(stateService);

        SlackAppServer server = new SlackAppServer(app);

        app.event(AppHomeOpenedEvent.class, (payload, ctx) -> {
            var myGames = games.get(ctx.getTeamId()).values().stream()
                    .filter(game -> game.getGameOwnerSlackId().equals(payload.getEvent().getUser())).collect(Collectors.toList());

            var appHomeView = view(view -> view
                    .type("home")
                    .blocks(asBlocks(
                                    actions(actions -> actions.elements(List.of(createEventButton))),
                                    section(section -> section.text(markdownText("*Your Games*"))),
                                    divider(),
                                    section(section -> section.text(markdownText("Game:ID"))),
                                    section(section -> section.text(
                                            markdownText(myGames.stream().map(game -> "*%s*:%s".formatted(game.getGameName()
                                                    , game.getGameUuid().toString())).collect(Collectors.joining(
                                                    "\n")))))
                            )
                    )

            );

            var res = ctx.client().viewsPublish(r -> r
                    .userId(payload.getEvent().getUser())
                    .view(appHomeView)
            );

            print(res);

            return ctx.ack();
        });

        app.blockAction(createEventButton.getActionId(), (blockActionRequest, ctx) -> {
            ctx.client().viewsOpen(viewsOpenRequestBuilder ->
                    viewsOpenRequestBuilder.viewAsString(Screens.CREATE_EVENT)
                            .triggerId(blockActionRequest.getContext().getTriggerId()));
            return ctx.ack();
        });

        // Listen to when the user submits the form
        app.viewSubmission(Screens.CREATE_EVENT_CALLBACK_ID, (viewSubmissionRequest, ctx) -> {
            var values = viewSubmissionRequest.getPayload().getView().getState().getValues();

            String teamListValue = extractValueByElementName(values, "teams").orElseThrow().getValue();
            String selectedChannel = extractValueByElementName(values, "channel").orElseThrow().getSelectedChannel();
            int teamsPerAllianceValue = Integer.parseInt(extractValueByElementName(values, "teams_per_alliance").orElseThrow()
                    .getValue());
            String gameName = extractValueByElementName(values, "event_name").orElseThrow().getValue();

            List<Team> teams;

            if (teamListValue.contains(",")) {

                teams = new ArrayList<>();
                for (String team : teamListValue.split(",")) {
                    var teamNum = team.trim();
                    if (teamNum.isEmpty() || !Pattern.matches("\\d+", teamNum) || teams.stream().anyMatch(
                            t -> t.number().equals(teamNum))) {
                        continue;
                    }
                    teams.add(new Team(teamNum));
                }
            } else {
                teams = getTeams.getTeamsAtEvent(teamListValue);
            }

            var game = new Game(selectedChannel, teamsPerAllianceValue, teams, ctx.getRequestUserId(), gameName);
            games.putIfAbsent(ctx.getTeamId(), new ConcurrentHashMap<>());
            games.get(ctx.getTeamId()).put(game.getGameUuid(), game);

            System.out.println(game);

            print(ctx.client().chatPostMessage(r -> r
                    .channel(selectedChannel)
                    .blocks(game.getGameRegistrationMessage())));

            save();
            System.out.printf("teamList: %s selectedChannel: %s teamsPerAlliance: %d%n", teamListValue, selectedChannel,
                    teamsPerAllianceValue);
            return ctx.ack();
        });

        app.blockAction("joinGame", (request, ctx) -> {
            Game game;
            try {
                game = games.get(ctx.getTeamId()).get(UUID.fromString(request.getPayload().getActions().get(0).getValue()));
            } catch (NullPointerException e) {
                return Response.ok(ctx.respond("Could not find your game"));
            }


            MethodsClient methods = ctx.client();
            var userId = request.getPayload().getUser().getId();
            System.out.println(userId);
            var realName = methods.usersInfo(r -> r.user(userId)).getUser().getProfile().getRealName();
            if (realName == null || realName.isEmpty()) {
                realName = request.getPayload().getUser().getName();
            }

            if (game.isFull()) {
                return Response.ok(ctx.respond("This game is full"));
            }
            if (game.getPlayers().stream().anyMatch(player -> player.slackId().equals(userId))) {
                return Response.ok(ctx.respond("You are already in this game"));
            }

            game.addPlayer(new Player(userId, realName));
            save();
            postLeaveJoinMessage(game, ctx, request);
            return ctx.ack();
        });

        app.blockAction("leaveGame", (request, ctx) -> {
            Game game;
            try {
                game = games.get(ctx.getTeamId()).get(UUID.fromString(request.getPayload().getActions().get(0).getValue()));
            } catch (NullPointerException e) {
                return Response.ok(ctx.respond("Could not find your game"));
            }

            var userId = request.getPayload().getUser().getId();
            game.removePlayer(userId);

            postLeaveJoinMessage(game, ctx, request);
            save();

            return ctx.ack();
        });

        app.blockAction("startGame", (request, ctx) -> {
            Game game;
            try {
                game = games.get(ctx.getTeamId()).get(UUID.fromString(request.getPayload().getActions().get(0).getValue()));
            } catch (NullPointerException e) {
                return Response.ok(ctx.respond("Could not find your game"));
            }

            var userId = request.getPayload().getUser().getId();
            if (!game.getGameOwnerSlackId().equals(userId)) {
                return Response.ok(ctx.respond("You don't have permission to start this game"));
            }

            if (game.getPlayers().isEmpty()) {
                return Response.ok(ctx.respond("You need a player to start a game"));
            }

            if (game.hasStarted() && false) {
                return Response.ok(ctx.respond("This game has already started"));
            } else {
                game.start();
                for (List<LayoutBlock> layoutBlocks : game.getDraftingMessage()) {
                    print(ctx.client().chatPostMessage(r -> r
                            .channel(game.getChannelId())
                            .blocks(layoutBlocks)));
                }
            }
            save();

            return ctx.ack();
        });

        app.blockAction(Pattern.compile("^pickTeam.*$"), (request, ctx) -> {
            try {
                String uuids = request.getPayload().getActions().get(0).getValue();
                String[] uuidsArray = uuids.split(",");

                Game game;
                try {
                    var teamId = ctx.getTeamId();
                    var gameId = UUID.fromString(uuidsArray[0]);
                    game = games.get(teamId).get(gameId);
                } catch (NullPointerException e) {
                    return Response.ok(ctx.respond("Could not find your game"));
                }

                var turnCount = uuidsArray[2];

                if (game.getTurnCount() != Long.parseLong(turnCount)) {
                    return Response.ok(ctx.respond("This picklist is out of date, scroll down to see the current pick!"));
                }

                var userId = request.getPayload().getUser().getId();
                var nextPlayerInDraft = game.getNextPlayerInDraft();
                if (nextPlayerInDraft != null && nextPlayerInDraft.slackId().equals(userId)) {
                    System.out.println(request.getPayload());
                    var teamUuid = UUID.fromString(uuidsArray[1]);
                    game.pickTeam(teamUuid);
                    for (List<LayoutBlock> layoutBlocks : game.getDraftingMessage()) {
                        print(ctx.client().chatPostMessage(r -> r
                                .channel(game.getChannelId())
                                .blocks(layoutBlocks)));
                    }
                } else {
                    return Response.ok(ctx.respond("It is not your turn to pick"));
                }

                save();

                return ctx.ack();
            } catch (Exception e) {
                e.printStackTrace();
                return Response.ok(ctx.respond("Something went wrong"));
            }
        });

        app.blockAction(pickTeamButton.getActionId(), (blockActionRequest, ctx) -> {
            try {
                var teamId = ctx.getTeamId();
                var gameId = UUID.fromString(blockActionRequest.getPayload().getActions().get(0).getValue());
                var game = games.get(teamId).get(gameId);
                var turnToPick = game.getNextPlayerInDraft();

                var availableTeams = game.getAvailableTeams();
                if (turnToPick == null) {
                    return Response.ok(ctx.respond("The draft is over"));
                }

                var teamsString = availableTeams.stream().map(Team::number).collect(Collectors.joining(", "));

                print(ctx.client().viewsOpen(viewsOpenRequestBuilder ->
                        viewsOpenRequestBuilder.viewAsString(
                                Screens.PICK_A_TEAM.formatted(Screens.PICK_TEAM_CALLBACK_ID + "," + gameId.toString(),
                                        "<@" + turnToPick.slackId() + ">",
                                        teamsString)).triggerId(blockActionRequest.getContext().getTriggerId())));

                return ctx.ack();
            } catch (Exception e) {
                e.printStackTrace();
                return Response.ok(ctx.respond("Something went wrong"));
            }
        });

        app.viewSubmission(Pattern.compile("^" + Screens.PICK_TEAM_CALLBACK_ID + ".*$"), (viewSubmissionRequest, ctx) -> {
            try {
                var values = viewSubmissionRequest.getPayload().getView().getState().getValues();
                var teamPickNumber = extractValueByElementName(values, "team_pick_number").orElseThrow().getValue();
                var teamId = ctx.getTeamId();

                var gameId = UUID.fromString(viewSubmissionRequest.getPayload().getView().getCallbackId().split(",")[1]);
                var game = games.get(teamId).get(gameId);
                var userId = viewSubmissionRequest.getPayload().getUser().getId();

                var nextPlayerInDraft = game.getNextPlayerInDraft();
                if (nextPlayerInDraft != null && nextPlayerInDraft.slackId().equals(userId)) {
                    game.pickTeam(teamPickNumber);
                    for (List<LayoutBlock> layoutBlocks : game.getDraftingMessage()) {
                        print(ctx.client().chatPostMessage(r -> r
                                .channel(game.getChannelId())
                                .blocks(layoutBlocks)));
                    }
                } else {
                    return ctx.ackWithErrors(Map.of("team_pick_number", "Something went wrong"));
                }

                game.pickTeam(teamPickNumber);

                save();
                return ctx.ack();
            } catch (Exception e) {
                e.printStackTrace();
                return ctx.ackWithErrors(Map.of("team_pick_number", "Something went wrong"));
            }
        });

        oauthApp.endpoint("GET", "/slack/oauth/completion", (req, ctx) -> {
            logger.info("completion: {}", req.getRequestBodyAsString());
            return Response.builder()
                    .statusCode(200)
                    .contentType("text/html")
                    .body(req.getRequestBodyAsString())
                    .build();
        });

        oauthApp.endpoint("GET", "/slack/oauth/cancellation", (req, ctx) -> {
            logger.info("cancellation: {}", req.getRequestBodyAsString());
            return Response.builder()
                    .statusCode(200)
                    .contentType("text/html")
                    .body(req.getRequestBodyAsString())
                    .build();
        });

        server.start();
    }


    // Define a function to extract a value by element name
    private static Optional<Value> extractValueByElementName(Map<String, Map<String, Value>> values, String elementName) {
        var elementData = values.values().stream()
                .filter(element -> element.containsKey(elementName))
                .findFirst();
        return elementData.map(stringValueMap -> stringValueMap.get(elementName));
    }

    private static void postLeaveJoinMessage(Game game, ActionContext ctx,
                                             BlockActionRequest request) throws SlackApiException, IOException {
        if (game.hasStarted()) {
            for (List<LayoutBlock> layoutBlocks : game.getDraftingMessage()) {
                print(ctx.client().chatPostMessage(r -> r
                        .channel(game.getChannelId())
                        .blocks(layoutBlocks)));
            }
        } else {
            print(ctx.client().chatUpdate(r -> r
                    .channel(game.getChannelId())
                    .ts(request.getPayload().getMessage().getTs())
                    .blocks(game.getGameRegistrationMessage())));
        }
    }

    public static void print(SlackApiResponse response) {
        if (!response.isOk()) {
            logger.warn(response.toString());
        }
    }

    public static void save() {
        try {
            FileOutputStream fos = new FileOutputStream("games.ser");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(games);
            oos.close();
            fos.close();
        } catch (Exception e) {
            System.out.println("Error saving");
            e.printStackTrace();
        }
    }
}