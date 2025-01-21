package Document_and_Data;

import java.io.Serializable;
import java.util.Objects;

public class Document implements Serializable, Comparable<Document> {
    private String name;

    // Default constructor required for deserialization
    public Document() {
    }

    // Parameterized constructor
    public Document(String name) {
        this.name = name;
    }

    // Getter method
    public String getName() {
        return name;
    }

    // Setter method (optional, depending on use case)
    public void setName(String name) {
        this.name = name;
    }

    // Override compareTo for sorting
    @Override
    public int compareTo(Document doc2) {
        return this.name.compareTo(doc2.getName());
    }

    // Override equals and hashCode for proper behavior in collections
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Document document = (Document) obj;
        return Objects.equals(name, document.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    // Override toString for better debugging and logging
    @Override
    public String toString() {
        return "Document{name='" + name + "'}";
    }
}
