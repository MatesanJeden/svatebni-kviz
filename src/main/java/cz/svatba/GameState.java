package cz.svatba;

import java.util.List;

public enum GameState {
    LOBBY,
    QUESTIONNAIRE,
    EVALUATION,
    RESULTS;

    public enum AnswerOption {
        NAT("Nat"),
        ANICKA("Anička"),
        OBA("Oba");

        private final String label;

        AnswerOption(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record Question(int id, String text) {}

    public record Player(
            String id,
            String name,
            boolean submitted,
            Long submittedAt,
            List<AnswerOption> answers,
            int score,
            int rank
    ) {}
}