package cz.svatba;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.svatba.GameState.AnswerOption;
import cz.svatba.GameState.Player;
import cz.svatba.GameState.Question;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuizManager {
    private final ObjectMapper mapper = new ObjectMapper();

    private GameState state = GameState.LOBBY;
    private final Map<String, Player> players = new ConcurrentHashMap<>();

    // Modifikovatelný seznam otázek
    private final List<Question> questions = new ArrayList<>(List.of(
            new Question(1, "Kdo je více upovídaný?"),
            new Question(2, "Kdo je více společenský?"),
            new Question(3, "Kdo podváděl ve škole při testech?"),
            new Question(4, "Kdo měl lepší známky z maturitního vysvědčení?"),
            new Question(5, "Kdo je větší optimista?"),
            new Question(6, "Komu by spíše chcípla kytka?"),
            new Question(7, "Kdo by spíše vyhrál vědomostní soutěž?"),
            new Question(8, "Kdo se bude víc roztahovat na posteli?"),
            new Question(9, "Kdo bude brát deku tomu druhému?"),
            new Question(10, "Kdo je větší romantik?"),
            new Question(11, "Kdo se zamiloval jako první?"),
            new Question(12, "Kdo byl nervóznější při první schůzce?"),
            new Question(13, "Kdo jako první začal mluvit o svatbě?")
    ));

    public static final List<String> GLOBAL_OPTIONS = List.of(
            AnswerOption.NAT.getLabel(),
            AnswerOption.ANICKA.getLabel(),
            AnswerOption.OBA.getLabel()
    );

    // Výchozí hodnota je null pro každou otázku (žádná není předvybraná)
    private final List<AnswerOption> correctAnswers = new ArrayList<>(
            Collections.nCopies(questions.size(), null)
    );

    public synchronized void registerPlayer(String id, String name) {
        players.putIfAbsent(id, new Player(id, name, false, null, List.of(), 0, 0));
    }

    public synchronized void submitAnswers(String id, List<AnswerOption> answers) {
        Player p = players.get(id);
        if (p != null) {
            players.put(id, new Player(p.id(), p.name(), true, System.currentTimeMillis(), answers, 0, 0));
        }
    }

    public synchronized void setCorrectAnswer(int questionIndex, AnswerOption option) {
        if (questionIndex >= 0 && questionIndex < correctAnswers.size()) {
            correctAnswers.set(questionIndex, option);
        }
    }

    public synchronized boolean areAllAnswersSet() {
        if (questions.isEmpty()) return false;
        for (AnswerOption opt : correctAnswers) {
            if (opt == null) return false;
        }
        return true;
    }

    public synchronized boolean changeState(GameState newState) {
        // Blokace přechodu do RESULTS, pokud moderátor nevyplnil vše
        if (newState == GameState.RESULTS && !areAllAnswersSet()) {
            System.out.println("--> Nelze vyhlásit výsledky: Nejsou vyplněny všechny odpovědi!");
            return false;
        }

        this.state = newState;
        if (newState == GameState.RESULTS) {
            recalculateLeaderboard();
        }
        return true;
    }

    public synchronized void resetGame() {
        this.state = GameState.LOBBY;
        this.players.clear();
        Collections.fill(correctAnswers, null);
    }

    public synchronized void addQuestion(String text) {
        if (state != GameState.LOBBY || text == null || text.isBlank()) return;
        int nextId = questions.stream().mapToInt(Question::id).max().orElse(0) + 1;
        questions.add(new Question(nextId, text.trim()));
        correctAnswers.add(null); // Nová otázka začíná bez vybrané odpovědi
    }

    public synchronized void removeQuestion(int index) {
        if (state != GameState.LOBBY || index < 0 || index >= questions.size()) return;
        questions.remove(index);
        correctAnswers.remove(index);
    }

    public synchronized void moveQuestion(int fromIndex, int toIndex) {
        if (state != GameState.LOBBY) return;
        if (fromIndex < 0 || fromIndex >= questions.size()) return;
        if (toIndex < 0) toIndex = 0;
        if (toIndex >= questions.size()) toIndex = questions.size() - 1;
        if (fromIndex == toIndex) return;

        Question q = questions.remove(fromIndex);
        AnswerOption ans = correctAnswers.remove(fromIndex);

        questions.add(toIndex, q);
        correctAnswers.add(toIndex, ans);
    }

    private void recalculateLeaderboard() {
        List<Player> evaluated = new ArrayList<>();
        for (Player p : players.values()) {
            int score = 0;
            if (p.submitted() && p.answers() != null) {
                for (int i = 0; i < questions.size() && i < p.answers().size(); i++) {
                    AnswerOption correct = correctAnswers.get(i);
                    if (correct != null && p.answers().get(i) == correct) {
                        score++;
                    }
                }
            }
            evaluated.add(new Player(p.id(), p.name(), p.submitted(), p.submittedAt(), p.answers(), score, 0));
        }

        evaluated.sort((a, b) -> {
            if (b.score() != a.score()) return Integer.compare(b.score(), a.score());
            long timeA = a.submittedAt() == null ? Long.MAX_VALUE : a.submittedAt();
            long timeB = b.submittedAt() == null ? Long.MAX_VALUE : b.submittedAt();
            return Long.compare(timeA, timeB);
        });

        int rank = 1;
        for (Player p : evaluated) {
            players.put(p.id(), new Player(p.id(), p.name(), p.submitted(), p.submittedAt(), p.answers(), p.score(), rank++));
        }
    }

    public synchronized String getFullStateJson() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("state", state.name());
            payload.put("questions", questions);
            payload.put("globalOptions", GLOBAL_OPTIONS);
            payload.put("correctAnswers", correctAnswers);
            payload.put("allAnswersSet", areAllAnswersSet());
            payload.put("players", players.values());
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}