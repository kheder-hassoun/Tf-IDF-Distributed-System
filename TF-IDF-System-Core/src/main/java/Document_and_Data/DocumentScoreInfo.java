package Document_and_Data;



import Document_and_Data.Document;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class DocumentScoreInfo implements Serializable {
    private Document document;
    private double score;

    public DocumentScoreInfo() {}

    public DocumentScoreInfo(Document document, double score) {
        this.document = document;
        this.score = score;
    }

    @Override
    public String toString() {
        return "DocumentScoreInfo{" +
                "document=" + document +
                ", score=" + score +
                '}';
    }
}
