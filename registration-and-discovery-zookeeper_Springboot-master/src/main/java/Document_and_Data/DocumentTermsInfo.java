package Document_and_Data;

import java.io.Serializable;
import java.util.HashMap;

public class DocumentTermsInfo implements Serializable {
    private Document document;
    private HashMap<String,Double> termFrequency;

    public DocumentTermsInfo() {
    }

    public DocumentTermsInfo(Document document, HashMap<String, Double> termFrequency) {
        this.document = document;
        this.termFrequency = termFrequency;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public HashMap<String, Double> getTermFrequency() {
        return termFrequency;
    }

    public void setTermFrequency(HashMap<String, Double> termFrequency) {
        this.termFrequency = termFrequency;
    }
}
