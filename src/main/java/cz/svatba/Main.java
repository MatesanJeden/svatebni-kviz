package cz.svatba;

import cz.svatba.GameState.Player;
import cz.svatba.GameState.Question;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.svatba.GameState.AnswerOption;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    // Místo natvrdo daného čísla 7070 načteme port z prostředí:
    private static final int PORT = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 7070;
    private static final String ADMIN_PIN = "1234"; // Váš tajný PIN moderátora

    private static final QuizManager quizManager = new QuizManager();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<WsContext> activeSessions = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        }).start(PORT);

        // Lehký endpoint pro UptimeRobot – vrátí jen bleskové "OK"
        app.get("/health", ctx -> ctx.result("OK").status(200));

        // Skrytá URL cesta pro moderátora
        app.get("/admin", ctx -> ctx.redirect("/admin.html"));

        System.out.println("==================================================");
        System.out.println("  Svatebčané (QR kód): http://localhost:" + PORT);
        System.out.println("  Moderátor / Svědek:   http://localhost:" + PORT + "/admin");
        System.out.println("==================================================");

        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                activeSessions.add(ctx);
                ctx.send(quizManager.getFullStateJson());
            });

            ws.onClose(activeSessions::remove);

            ws.onMessage(ctx -> {
                try {
                    JsonNode node = mapper.readTree(ctx.message());
                    String type = node.path("type").asText();

                    switch (type) {
                        case "JOIN" -> {
                            quizManager.registerPlayer(node.path("id").asText(), node.path("name").asText());
                            broadcastState();
                        }
                        case "SUBMIT" -> {
                            String id = node.path("id").asText();
                            List<AnswerOption> answers = new ArrayList<>();
                            for (JsonNode ans : node.path("answers")) {
                                answers.add(AnswerOption.valueOf(ans.asText()));
                            }
                            quizManager.submitAnswers(id, answers);
                            broadcastState();
                        }
                        case "ADMIN_SET_ANSWER" -> {
                            if (verifyPin(node)) {
                                int qIdx = node.path("qIdx").asInt();
                                AnswerOption option = AnswerOption.valueOf(node.path("option").asText());
                                quizManager.setCorrectAnswer(qIdx, option);
                                broadcastState();
                            }
                        }
                        case "ADMIN_CHANGE_STATE" -> {
                            if (verifyPin(node)) {
                                GameState newState = GameState.valueOf(node.path("state").asText());
                                boolean changed = quizManager.changeState(newState);
                                if (changed) {
                                    broadcastState();
                                }
                            }
                        }
                        case "ADMIN_RESET" -> {
                            if (verifyPin(node)) {
                                System.out.println("--> Reset hry moderátorem");
                                quizManager.resetGame();
                                broadcastState();
                            }
                        }
                        case "ADMIN_ADD_QUESTION" -> {
                            if (verifyPin(node)) {
                                String text = node.path("text").asText();
                                quizManager.addQuestion(text);
                                broadcastState();
                            }
                        }
                        case "ADMIN_REMOVE_QUESTION" -> {
                            if (verifyPin(node)) {
                                int index = node.path("index").asInt();
                                quizManager.removeQuestion(index);
                                broadcastState();
                            }
                        }
                        case "ADMIN_MOVE_QUESTION" -> {
                            if (verifyPin(node)) {
                                int from = node.path("from").asInt();
                                int to = node.path("to").asInt();
                                quizManager.moveQuestion(from, to);
                                broadcastState();
                            }
                        }
                        case "ADMIN_RESTORE" -> {
                            if (verifyPin(node)) {
                                JsonNode data = node.path("data");
                                GameState st = GameState.valueOf(data.path("state").asText("LOBBY"));
                                
                                List<Question> qs = new ArrayList<>();
                                for (JsonNode qn : data.path("questions")) {
                                    qs.add(mapper.treeToValue(qn, Question.class));
                                }

                                List<AnswerOption> ans = new ArrayList<>();
                                for (JsonNode an : data.path("correctAnswers")) {
                                    if (an.isNull()) {
                                        ans.add(null);
                                    } else {
                                        ans.add(AnswerOption.valueOf(an.asText()));
                                    }
                                }

                                List<Player> pls = new ArrayList<>();
                                for (JsonNode pn : data.path("players")) {
                                    pls.add(mapper.treeToValue(pn, Player.class));
                                }

                                quizManager.restoreFromBackup(st, qs, ans, pls);
                                broadcastState();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
    }

    private static boolean verifyPin(JsonNode node) {
        return ADMIN_PIN.equals(node.path("pin").asText());
    }

    private static void broadcastState() {
        String json = quizManager.getFullStateJson();
        for (WsContext ctx : activeSessions) {
            if (ctx.session.isOpen()) {
                ctx.send(json);
            }
        }
    }
}