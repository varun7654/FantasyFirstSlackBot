package com.dacubeking.fantasyfirst;

import com.dacubeking.fantasyfirst.game.Game;
import com.dacubeking.fantasyfirst.game.Game.Player;
import com.dacubeking.fantasyfirst.game.Game.Team;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.jetty.SlackAppServer;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.bolt.response.Response;
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

    public static final String ADMIN_USER_ID = "U05L2LK6GCX";

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
        try (FileInputStream fis = new FileInputStream("games.ser")) {
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object object = ois.readObject();
            var gamesRead = (ConcurrentMap<String, ConcurrentMap<UUID, Game>>) object;
            games.putAll(gamesRead);
        } catch (FileNotFoundException e) {
            System.out.println("No save file found");
            e.printStackTrace();
        } catch (ClassNotFoundException | ClassCastException e) {
            System.out.println("Save file is corrupted");
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        GetTeamsAtEvent getTeams = new GetTeamsAtEvent(Creds.TBA_API_KEY);

        var appConfig = new AppConfig();
//        new File(DATA_DIR).mkdirs();
//        InstallationService installationService = new FileInstallationService(appConfig, DATA_DIR);
//        installationService.setHistoricalDataEnabled(true);

        appConfig.setSigningSecret(Creds.SLACK_SIGNING_KEY);
        appConfig.setSingleTeamBotToken(Creds.SLACK_BOT_TOKEN);

        var app = new App(appConfig);
        //app.service(installationService);

        App oauthApp = new App(AppConfig.builder()
                .clientId(Creds.SLACK_CLIENT_ID)
                .signingSecret(Creds.SLACK_SIGNING_KEY)
                .clientSecret(Creds.SLACK_CLIENT_SECRET)
                .redirectUri("https://dacubeking.com/slack/oauth/completion")
                .scope("chat:write,chat:write.public,users:read,users.profile:read")
                .appPath("/slack/oauth/start")
                .oauthCallbackPath("/slack/oauth/callback")
                .oauthCancellationUrl("https://dacubeking.com/slack/oauth/cancellation")
                .oauthCompletionUrl("https://dacubeking.com/slack/oauth/completion")
                .build()).asOAuthApp(true);

        SlackAppServer server = new SlackAppServer(new HashMap<>(Map.of(
                "/slack/events", app, // POST /slack/events (incoming API requests from the Slack Platform)
                "/slack/oauth", oauthApp // GET  /slack/oauth/start, /slack/oauth/callback (user access)
        )));

        //SlackAppServer server = new SlackAppServer(app);

        app.event(AppHomeOpenedEvent.class, (payload, ctx) -> {
            var myGames = games.get(ctx.getTeamId()).values().stream()
                    .filter(game -> game.getGameOwnerSlackId().equals(payload.getEvent().getUser())).collect(Collectors.toList());

            var gamesText = myGames.stream().map(
                            game -> {
                                var list = new ArrayList<String>();
                                list.add("*%s* (%s) (%s)\n%s"
                                        .formatted(game.getGameName(), game.getGameUuid().toString(),
                                                game.getLastMessagesTs() != null && !game.getLastMessagesTs().isEmpty() ?
                                                        getMessageLink(ctx, game.getChannelId(), game.getLastMessagesTs().get(0))
                                                        : "no message link",
                                                game.getMarkdownTable()));
                                return list;
                            })
                    .flatMap(Collection::stream)
                    .collect(Collectors.joining("\n"));


            var appHomeView = view(view -> view
                    .type("home")
                    .blocks(asBlocks(
                                    actions(actions -> actions.elements(List.of(createEventButton))),
                                    section(section -> section.text(markdownText("*Your Games*"))),
                                    divider(),
                                    section(section -> section.text(markdownText(gamesText)))
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

            if (gameName.trim().endsWith("Draft")) {
                gameName = gameName.trim().substring(0, gameName.trim().length() - 5);
            }

            var game = new Game(selectedChannel, teamsPerAllianceValue, teams, ctx.getRequestUserId(), gameName);
            games.putIfAbsent(ctx.getTeamId(), new ConcurrentHashMap<>());
            games.get(ctx.getTeamId()).put(game.getGameUuid(), game);

            System.out.println(game);

            game.setLastMessagesTs(List.of(ctx.client().chatPostMessage(r -> r
                    .channel(selectedChannel)
                    .blocks(game.getGameRegistrationMessage())).getTs()));

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
            if (!game.getGameOwnerSlackId().equals(userId) && !userId.equals(ADMIN_USER_ID)) {
                return Response.ok(ctx.respond("You don't have permission to start this game"));
            }

            if (game.getPlayers().isEmpty()) {
                return Response.ok(ctx.respond("You need a player to start a game"));
            }

            if (game.hasStarted()) {
                return Response.ok(ctx.respond("This game has already started"));
            } else {
                var splitPlayers = game.splitPlayers();

                game.getPlayers().clear();
                game.getPlayers().addAll(splitPlayers.get(0));
                List<Game> gamesFromThisGame = new ArrayList<>();
                gamesFromThisGame.add(game);


                for (int i = 1; i < splitPlayers.size(); i++) {
                    var newGame = new Game(game.getChannelId(), game.getTeamsPerAlliance(), game.getTeams(),
                            game.getGameOwnerSlackId(), game.getGameName() + " " + (i + 1));
                    newGame.getPlayers().addAll(splitPlayers.get(i));
                    gamesFromThisGame.add(newGame);
                    games.get(ctx.getTeamId()).put(newGame.getGameUuid(), newGame);
                }

                if (splitPlayers.size() > 1) {
                    game.setGameName(game.getGameName() + " 1");
                }


                for (Game gameFromThisGame : gamesFromThisGame) {
                    gameFromThisGame.start();
                    var messageTs = new ArrayList<String>();
                    for (List<LayoutBlock> layoutBlocks : gameFromThisGame.getDraftingMessage()) {
                        messageTs.add(ctx.client().chatPostMessage(r -> r
                                .channel(gameFromThisGame.getChannelId())
                                .blocks(layoutBlocks)).getTs());
                    }
                    gameFromThisGame.setLastMessagesTs(messageTs);
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

                if (!game.hasStarted()) {
                    return Response.ok(ctx.respond("The game has not started"));
                }

                var userId = request.getPayload().getUser().getId();
                var nextPlayerInDraft = game.getNextPlayerInDraft();
                if (nextPlayerInDraft != null && nextPlayerInDraft.slackId().equals(userId)) {
                    System.out.println(request.getPayload());
                    var teamUuid = UUID.fromString(uuidsArray[1]);
                    if (!game.pickTeam(teamUuid)) {
                        return Response.ok(ctx.respond("This team is not available"));
                    }
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
                    var messageTs = new ArrayList<String>();
                    for (List<LayoutBlock> layoutBlocks : game.getDraftingMessage()) {
                        messageTs.add(ctx.client().chatPostMessage(r -> r
                                .channel(game.getChannelId())
                                .blocks(layoutBlocks)).getTs());
                    }

                    if (game.getLastMessagesTs() != null) {
                        for (String s : game.getLastMessagesTs()) {
                            ctx.client().chatDelete(r -> r.channel(game.getChannelId()).ts(s));
                        }
                    }

                    game.setLastMessagesTs(messageTs);
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

        app.command("/debug", (req, ctx) -> {
            // Format it /debug gameUUID args...
            String[] dArgs = req.getPayload().getText().split(" ");
            System.out.println("dargs: " + Arrays.toString(dArgs));

            if (ctx.getRequestUserId().equalsIgnoreCase(ADMIN_USER_ID) && (dArgs[0].equalsIgnoreCase("pGames"))) {
                return ctx.ack("```" + games.toString() + "```");
            }


            UUID gameUUID = UUID.fromString(dArgs[0]);
            System.out.println(gameUUID);
            Game game = games.get(ctx.getTeamId()).get(gameUUID);
            if (game == null) {
                return ctx.ack("Game not found");
            }

            if (!Objects.equals(ctx.getRequestUserId(), game.getGameOwnerSlackId())
                    && !Objects.equals(ctx.getRequestUserId(), ADMIN_USER_ID)) {
                return ctx.ack("You are not the owner of this game");
            }

            dArgs = Arrays.copyOfRange(dArgs, 1, dArgs.length);

            if (dArgs[0].equalsIgnoreCase("pPlayers")) {
                return ctx.ack("```" + game.getPlayers().toString() + "```");
            }

            if (dArgs[0].equalsIgnoreCase("pTeams")) {
                return ctx.ack("```" + game.getTeams().toString() + "```");
            }

            if (dArgs[0].equalsIgnoreCase("addPlayer")) {

                ArrayList<Player> playersAdded = new ArrayList<>();
                for (int i = 1; i < dArgs.length; i++) {
                    String playerSlackId = Utils.getSlackIdFromMention(dArgs[i]);

                    var realName = ctx.client().usersInfo(r -> r.user(playerSlackId)).getUser().getProfile().getRealName();
                    if (realName == null || realName.isEmpty()) {
                        realName = ctx.client().usersInfo(r -> r.user(playerSlackId)).getUser().getName();
                    }
                    if (game.getPlayers().stream().noneMatch(player -> player.slackId().equals(playerSlackId))) {
                        game.addPlayer(new Player(playerSlackId, realName));
                        playersAdded.add(new Player(playerSlackId, realName));
                    }
                }


                save();

                return ctx.ack("Added player : " + playersAdded.toString());
            }

            if (dArgs[0].equalsIgnoreCase("removePlayer")) {
                String playerSlackId = Utils.getSlackIdFromMention(dArgs[1]);
                game.removePlayer(playerSlackId);
                save();
                return ctx.ack("Removed player : " + playerSlackId);
            }

            if (dArgs[0].equalsIgnoreCase("unStartGame")) {
                game.unStart();
                save();
                return ctx.ack("Game unstarted");
            }

            if (dArgs[0].equalsIgnoreCase("reprint")) {
                if (game.hasStarted()) {
                    var messageTs = new ArrayList<String>();
                    for (List<LayoutBlock> layoutBlocks : game.getDraftingMessage()) {
                        messageTs.add(ctx.client().chatPostMessage(r -> r
                                .channel(game.getChannelId())
                                .blocks(layoutBlocks)).getTs());
                    }
                    game.setLastMessagesTs(messageTs);
                } else {
                    game.setLastMessagesTs(List.of(ctx.client().chatPostMessage(r -> r
                            .channel(game.getChannelId())
                            .blocks(game.getGameRegistrationMessage())).getTs()));
                }
                return ctx.ack("Reprinted");
            }

            if (dArgs[0].equalsIgnoreCase("setTargetPlayerCount")) {
                game.setTargetPlayerCount(Integer.parseInt(dArgs[1]));
                save();
                return ctx.ack("Set target player count to " + dArgs[1]);
            }

            if (dArgs[0].equalsIgnoreCase("rename")) {
                String name = "";
                for (int i = 1; i < dArgs.length; i++) {
                    name += dArgs[i] + " ";
                }
                game.setGameName(name.trim());
                save();
                return ctx.ack("Renamed game to " + name.trim());
            }

            if (dArgs[0].equalsIgnoreCase("previewSplit")) {
                return ctx.ack("``` " + game.splitPlayers().toString() + " ```");
            }

            if (dArgs[0].equalsIgnoreCase("delete")) {
                games.get(ctx.getTeamId()).remove(gameUUID);
                save();
                return ctx.ack("Deleted game");
            }

            return ctx.ack("```" + games.toString() + "```");
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

    public static SlackApiResponse print(SlackApiResponse response) {
        if (!response.isOk()) {
            logger.warn(response.toString());
        }
        return response;
    }

    public static void save() {
        try (FileOutputStream fos = new FileOutputStream("games.ser");) {
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(games);
            oos.close();
        } catch (Exception e) {
            System.out.println("Error saving");
            e.printStackTrace();
        }
    }

    public static String getMessageLink(EventContext ctx, String channel, String ts) {
        if (ts == null || ts.isEmpty() || channel == null || channel.isEmpty() || ctx.getTeamId() == null) {
            return "no message link";
        }
        return "https://" + ctx.getTeamId() + ".slack.com/archives/" + channel + "/p" + ts.replace(".", "");
    }
}