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
            new Question(1, "Kdo udělal první krok k seznámení?"),
            new Question(2, "Kdo doma častěji a raději vaří?"),
            new Question(3, "Kdo má větší tendenci chodit pozdě?"),
            new Question(4, "Kdo tráví víc času vybíráním filmu před spaním?"),
            new Question(5, "Kdo lépe parkuje autem?"),
            new Question(6, "Kdo je ranní ptáče a vstává dříve?"),
            new Question(7, "Kdo víc plánuje a organizuje společné výlety?")
    ));

    public static final List<String> GLOBAL_OPTIONS = List.of(
            AnswerOption.NAT.getLabel(),
            AnswerOption.ANICKA.getLabel(),
            AnswerOption.OBA.getLabel()
    );

    // Správné odpovědi vždy udržují stejnou velikost jako questions
    private final List<AnswerOption> correctAnswers = new ArrayList<>(
            Collections.nCopies(questions.size(), AnswerOption.NAT)
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

    public synchronized void changeState(GameState newState) {
        this.state = newState;
        if (newState == GameState.RESULTS) {
            recalculateLeaderboard();
        }
    }

    public synchronized void resetGame() {
        this.state = GameState.LOBBY;
        this.players.clear();
        Collections.fill(correctAnswers, AnswerOption.NAT);
    }

    // --- SPRÁVA OTÁZEK (POVOLENA POUZE V LOBBY) ---

    public synchronized void addQuestion(String text) {
        if (state != GameState.LOBBY || text == null || text.isBlank()) return;
        int nextId = questions.stream().mapToInt(Question::id).max().orElse(0) + 1;
        questions.add(new Question(nextId, text.trim()));
        correctAnswers.add(AnswerOption.NAT);
    }

    public synchronized void removeQuestion(int index) {
        if (state != GameState.LOBBY || index < 0 || index >= questions.size()) return;
        questions.remove(index);
        correctAnswers.remove(index);
    }

    public synchronized void moveQuestion(int fromIndex, int toIndex) {
        if (state != GameState.LOBBY) return;
        if (fromIndex < 0 || fromIndex >= questions.size() || toIndex < 0 || toIndex >= questions.size()) return;

        Question q = questions.remove(fromIndex);
        questions.add(toIndex, q);

        AnswerOption ans = correctAnswers.remove(fromIndex);
        correctAnswers.add(toIndex, ans);
    }

    private void recalculateLeaderboard() {
        List<Player> evaluated = new ArrayList<>();
        for (Player p : players.values()) {
            int score = 0;
            if (p.submitted() && p.answers() != null) {
                for (int i = 0; i < questions.size() && i < p.answers().size(); i++) {
                    if (p.answers().get(i) == correctAnswers.get(i)) {
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
            payload.put("players", players.values());
            return mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}