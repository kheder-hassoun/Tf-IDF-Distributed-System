package Document_and_Data;

import java.io.Serializable;

public class Document implements Serializable, Comparable<Document> {
    private String name;

    public Document(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }


    @Override
    public int compareTo(Document doc2) {
        return this.name.compareTo(doc2.getName());

    }
}
