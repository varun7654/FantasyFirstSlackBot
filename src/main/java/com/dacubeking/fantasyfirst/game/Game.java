package com.dacubeking.fantasyfirst.game;

import com.dacubeking.fantasyfirst.Main;
import com.slack.api.model.block.LayoutBlock;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.asElements;
import static com.slack.api.model.block.element.BlockElements.button;
import static java.lang.Math.max;

public class Game implements Serializable {

    private static final long serialVersionUID = -8421916417899141622L;

    public record Team(String name, String number, String elo, UUID uuid) implements Serializable {
        public Team(String number) {
            this(number, number, "0", UUID.randomUUID());
        }
    }

    public record Player(String slackId, String name, ArrayList<Team> selectedTeams) implements Serializable {
        public Player(String slackId, String name) {
            this(slackId, name, new ArrayList<>());
        }
    }

    private final ArrayList<Team> availableTeams;

    private final ArrayList<Player> players = new ArrayList<>();

    private final String channelId;
    private final int allianceSize;
    private final UUID uuid = UUID.randomUUID();

    private final String gameOwnerSlackId;
    private final String gameName;
    private boolean hasStarted = false;
    private long turnCount = 0;
    private @Nullable List<String> lastMessagesTs;
    private int targetPlayersPerGame = 0;

    /**
     * Create a new game
     *
     * @param channelId        The channel that the game is in
     * @param allianceSize     The number of teams that each player will pick
     * @param teams            The teams that are available to be picked
     * @param gameOwnerSlackId The slack id of the person who created the game
     * @param gameName         The name of the game
     *                         <p>
     *                         NOTE: The max players is set to 0, meaning there is no limit to the number of players that can join
     *                         the game
     */
    public Game(String channelId, int allianceSize, List<Team> teams, String gameOwnerSlackId, String gameName) {
        this(channelId, allianceSize, teams, gameOwnerSlackId, gameName, 0);
    }

    /**
     * Create a new game
     *
     * @param channelId            The channel that the game is in
     * @param allianceSize         The number of teams that each player will pick
     * @param teams                The teams that are available to be picked
     * @param gameOwnerSlackId     The slack id of the person who created the game
     * @param gameName             The name of the game
     * @param targetPlayersPerGame The maximum number of players that can be in the game. If 0, there is no limit
     */
    public Game(String channelId, int allianceSize, List<Team> teams, String gameOwnerSlackId, String gameName,
                int targetPlayersPerGame) {
        this.channelId = channelId;
        this.allianceSize = allianceSize;
        this.availableTeams = new ArrayList<>(teams);
        this.gameOwnerSlackId = gameOwnerSlackId;
        this.gameName = gameName;
        availableTeams.sort(Comparator.comparingInt(o -> Integer.parseInt(o.number)));
        this.targetPlayersPerGame = targetPlayersPerGame;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void addTeam(Team team) {
        availableTeams.add(team);
        availableTeams.sort(Comparator.comparingInt(o -> Integer.parseInt(o.number)));
    }

    public void removeTeam(Team team) {
        availableTeams.remove(team);

        for (Player player : players) {
            player.selectedTeams.remove(team);
        }
    }

    public void removePlayer(Player player) {
        if (players.remove(player)) {
            availableTeams.addAll(player.selectedTeams);
        }
    }

    public void removePlayer(String slackId) {
        for (Player player : players) {
            if (player.slackId.equals(slackId)) {
                removePlayer(player);
                return;
            }
        }
    }

    public UUID getGameUuid() {
        return uuid;
    }

    public String getChannelId() {
        return channelId;
    }

    public int getAllianceSize() {
        return allianceSize;
    }

    public List<Team> getAvailableTeams() {
        return availableTeams;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public boolean isFull() {
        return !hasStarted() || (players.size() >= availableTeams.size() / allianceSize);
    }

    public String getGameOwnerSlackId() {
        return gameOwnerSlackId;
    }

    public String getGameName() {
        return gameName;
    }

    public boolean hasStarted() {
        return hasStarted;
    }

    public void start() {
        if (hasStarted) {
            return;
        }
        Collections.shuffle(players);
        hasStarted = true;
    }

    @Override
    public String toString() {
        return "Game{" +
                "availableTeams=" + availableTeams +
                ", players=" + players +
                ", channelId='" + channelId + '\'' +
                ", allianceSize=" + allianceSize +
                ", uuid=" + uuid +
                ", gameOwnerSlackId='" + gameOwnerSlackId + '\'' +
                '}';
    }

    // Help me write a message that allows users to register for the game. The message should say: "A Fantasy First Game has
    // been created" and then list out the teams that are available to be selected. The message should also have a button
    // that says "Join Game". When the user clicks the button, they should be added to the game. There should also be a "Leave
    // Game" button that removes the user from the game. The message should also have a button that says "Start Game". When
    // the user clicks the button, the game should start.

    public List<LayoutBlock> getGameRegistrationMessage() {

        String playersString = players.stream().map(Player::name).collect(Collectors.joining(", "));
        if (playersString.isEmpty()) {
            playersString = "No players have joined yet";
        }
        String finalPlayersString = playersString;

        return asBlocks(
                section(section -> section.text(markdownText("*A Fantasy First Game has been created: " + gameName + "*"))),
                section(section -> section.text(markdownText("Teams:"))),
                section(section -> section.text(
                        plainText(availableTeams.stream().map(Team::name).collect(Collectors.joining(", "))))),
                divider(),
                section(section -> section.text(markdownText("Players:"))),
                section(section -> section.text(plainText(finalPlayersString))),
                getJoiningButtons()
        );
    }

    public List<List<LayoutBlock>> getDraftingMessage() {
        turnCount++;
        // Print out the current state of the draft in a table
        LayoutBlock table = section(section -> section.fields(
                players.stream().map(player ->
                        markdownText(
                                "*" + player.name() + "*: " +
                                        player.selectedTeams().stream().map(Team::name).collect(Collectors.joining(", ")
                                        ))).collect(Collectors.toList())
        ));
        // Get the next person in the draft order
        var nextPlayerInDraft = getNextPlayerInDraft();
        if (nextPlayerInDraft == null) {
            return List.of(asBlocks(
                    section(section -> section.text(markdownText("The *" + getGameName() + "* draft is over!"))),
                    table
            ));
        } else {
            var messages = new ArrayList<List<LayoutBlock>>();
            Main.pickTeamButton.setValue(getGameUuid().toString());
            messages.add(asBlocks(
                    section(section -> section.text(markdownText("*" + getGameName() + " Draft:*"))),
                    section(section -> section.text(markdownText("It is <@" + nextPlayerInDraft.slackId()
                            + ">'s turn to pick a team"))),
                    table,
                    actions(actions -> actions.elements(List.of(Main.pickTeamButton))),
                    section(section -> section.text(markdownText("You can still join the draft!"))),
                    getJoiningButtons()
            ));
//            messages.addAll(
//                    splitTeams.stream().map(groupedTeams -> asBlocks(
//                            actions("DraftingButtons", groupedTeams.stream()
//                                    .map(team -> button(b -> b.text(plainText(pt -> pt.text(team.name)))
//                                            .actionId("pickTeam" + team.uuid)
//                                            .value(getGameUuid() + "," + team.uuid().toString() + "," + turnCount)))
//                                    .collect(Collectors.toList()))
//                    )).collect(Collectors.toList())
//            );
//            messages.add(asBlocks(
//                    section(section -> section.text(markdownText("You can still join the draft!"))),
//                    getJoiningButtons()
//            ));
            return messages;
        }
    }


    public Player getNextPlayerInDraft() {
        for (int i = 1; i <= allianceSize; i++) {
            boolean reverseOrder = i % 2 == 0;
            var players = this.players;
            if (reverseOrder) {
                players = new ArrayList<>(players);
                Collections.reverse(players);
            }

            for (var player : players) {
                if (player.selectedTeams().size() < i) {
                    return player;
                }
            }
        }
        return null;
    }

    public LayoutBlock getJoiningButtons() {
        String gameUuid = getGameUuid().toString();
        if (hasStarted) {
            return actions("GameCreationButtons", asElements(
                    button(b -> b.text(plainText(pt -> pt.text("Join Game"))).actionId("joinGame").value(gameUuid)),
                    button(b -> b.text(plainText(pt -> pt.text("Leave Game"))).actionId("leaveGame").value(gameUuid))
            ));
        } else {
            return actions("GameCreationButtons", asElements(
                    button(b -> b.text(plainText(pt -> pt.text("Join Game"))).actionId("joinGame").value(gameUuid)),
                    button(b -> b.text(plainText(pt -> pt.text("Leave Game"))).actionId("leaveGame").value(gameUuid)),
                    button(b -> b.text(plainText(pt -> pt.text("Start Draft"))).actionId("startGame").value(gameUuid))
            ));
        }
    }

    /**
     * Pick a team for the next player in the draft. The next player in the draft is automatically determined by the game. It is
     * imperative that calling code ensures that the game has not ended before calling this method.
     *
     * @param teamUuid The UUID of the team to pick
     * @return true if the team was picked, false if the team was not picked
     */
    public boolean pickTeam(UUID teamUuid) {
        for (var team : availableTeams) {
            if (team.uuid().equals(teamUuid)) {
                var nextPlayerInDraft = getNextPlayerInDraft();
                if (nextPlayerInDraft != null) {
                    nextPlayerInDraft.selectedTeams.add(team);
                    availableTeams.remove(team);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Pick a team for the next player in the draft. The next player in the draft is automatically determined by the game. It is
     * imperative that calling code ensures that the game has not ended before calling this method.
     *
     * @param teamNum The number of the team to pick
     */
    public void pickTeam(String teamNum) {
        for (var team : availableTeams) {
            if (team.number.equals(teamNum)) {
                var nextPlayerInDraft = getNextPlayerInDraft();
                if (nextPlayerInDraft != null) {
                    nextPlayerInDraft.selectedTeams.add(team);
                    availableTeams.remove(team);
                }
                return;
            }
        }
    }

    public long getTurnCount() {
        return turnCount;
    }

    /**
     * Get a markdown table of the current state of the draft
     *
     * @return A markdown table of the current state of the draft
     */
    public String getMarkdownTable() {
        int longestTeamName = max(availableTeams.stream().map(Team::name).mapToInt(String::length).max().orElse(0),
                4 + String.valueOf(allianceSize).length());
        int longestName = max(players.stream().map(Player::name).mapToInt(String::length).max().orElse(0), 4);

        String table = "";
        table += "```";
        table += "Name" + " ".repeat(longestName - 4) + " | ";
        for (int i = 0; i < allianceSize; i++) {
            table += "team" + (i + 1) + " ".repeat(longestTeamName - 5) + " | ";
        }
        table += "\n";

        var divider = "-".repeat(table.length() - 5) + "\n";
        table += divider;
        for (var player : players) {
            table += player.name + " ".repeat(longestName - player.name.length()) + " | ";
            for (int i = 0; i < allianceSize; i++) {
                if (player.selectedTeams().size() > i) {
                    table += player.selectedTeams().get(i).name + " "
                            .repeat(longestTeamName - player.selectedTeams().get(i).name.length()) + " | ";
                } else {
                    table += " ".repeat(longestTeamName) + " | ";
                }
            }
            table += "\n";
        }
        table += divider;
        table += "```";

        return table;
    }

    public @Nullable List<String> getLastMessagesTs() {
        return lastMessagesTs;
    }


    public void setLastMessagesTs(@Nullable List<String> lastMessagesTs) {
        this.lastMessagesTs = lastMessagesTs;
    }

    /**
     * Split the players into groups of maxPlayers
     *
     * @return A list of lists of players, where each list of players is a group of players that will play together
     */
    @Contract(pure = true)
    public List<List<Player>> splitPlayers() {
        if (targetPlayersPerGame > 0) {
            var playersCopy = new ArrayList<>(players);

            int actualMaxPlayers = availableTeams.size() / allianceSize;
            int maxPlayers = Math.min(this.targetPlayersPerGame, actualMaxPlayers);

            // Try to split the players into groups are so that the number of players in each group is as close to maxPlayers as possible
            // We are ok with going over maxPlayers, as long as we don't go over actualMaxPlayers.

            int numGroups = (int) Math.round((double) playersCopy.size() / maxPlayers);

            int playersPerGroup = (int) Math.ceil((double) playersCopy.size() / numGroups);
            if (playersPerGroup > actualMaxPlayers) {
                playersPerGroup = actualMaxPlayers;
                numGroups++;
            }

            List<List<Player>> splitPlayers = new ArrayList<>();

            Collections.shuffle(playersCopy);

            for (int i = 0; i < numGroups; i++) {
                int start = i * playersPerGroup;
                int end = Math.min((i + 1) * playersPerGroup, playersCopy.size());
                splitPlayers.add(playersCopy.subList(start, end));
            }
            return splitPlayers;
        }
        return List.of(players);
    }
}
