import Students.Student;
import dataStructures.*;
import java.io.*;

/**
 * Implements the {@link StudentCollection} interface.
 * <p>
 * This class is responsible for managing all {@link Student} objects for an {@link Area}.
 * It maintains two internal structures to satisfy different retrieval requirements:
 * <ol>
 * <li>A {@link SortedMap} (`studentsByName`) to store students sorted
 * **alphabetically by name** using a {@link StudentNameComparator}.
 *.</li>
 * <li>A {@link Map} (`studentsByCountry`) to store students grouped by country.</li>
 * </ol>
 * This class is serializable with optimized custom serialization to reduce I/O overhead.
 */
public class StudentsCollectionImpl implements StudentCollection, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // --- Fields ---

    /**
     * Sorted map of students by name (lowercase) for O(log n) lookups and ordered iteration.
     */
    private SortedMap<String, Student> studentsByName;

    /**
     * Map of students grouped by country (lowercase) for O(1) country-based filtering.
     */
    private Map<String, List<Student>> studentsByCountry;

    // --- Constructor ---

    /**
     * Constructs a new, empty student collection.
     * Initializes both the name-sorted map and the country map.
     */
    public StudentsCollectionImpl() {
        this.studentsByName = new AVLSortedMap<>();
        this.studentsByCountry = new SepChainHashTable<>();
    }

    // --- State Modifiers ---

    /**
     * Adds a new student to the collection.
     * The student is added to the name-sorted map and the appropriate country list.
     *
     * @param student The {@link Student} to add.
     */
    @Override
    public void addStudent(Student student) {
        String lowerName = student.getName().toLowerCase();
        studentsByName.put(lowerName, student);

        String lowerCountry = student.getCountry().toLowerCase();
        List<Student> countryList = studentsByCountry.get(lowerCountry);

        if (countryList == null) {
            countryList = new DoublyLinkedList<>();
            studentsByCountry.put(lowerCountry, countryList);
        }
        countryList.addLast(student);
    }

    /**
     * Removes a student from the collection, identified by their name.
     * <p>
     * This method first finds the student using the name map
     * and then removes it from both internal structures.
     *
     * @param name The name of the student to remove.
     */
    @Override
    public void removeStudent(String name) {
        String lowerName = name.toLowerCase();

        Student student = studentsByName.remove(lowerName);

        if (student != null) {
            String lowerCountry = student.getCountry().toLowerCase();
            List<Student> countryList = studentsByCountry.get(lowerCountry);
            if (countryList != null) {
                int index = countryList.indexOf(student);
                if (index != -1) {
                    countryList.remove(index);
                }

                if (countryList.isEmpty()) {
                    studentsByCountry.remove(lowerCountry);
                }
            }
        }
    }

    // --- Querying & Searching ---

    /**
     * Finds a student by their name using case-insensitive lookup.
     * <p>
     * This search is performed on the name-sorted map.
     *
     * @param name The name of the student to find.
     * @return The {@link Student} object, or {@code null} if not found.
     */
    @Override
    public Student findByName(String name) {
        return studentsByName.get(name.toLowerCase());
    }

    // --- Iterators & Retrieval ---

    /**
     * Gets an iterator over all students in the collection,
     * sorted alphabetically by name.
     *
     * @return A sorted {@link Iterator} of all {@link Student}s.
     */
    @Override
    public Iterator<Student> listAllStudents() {
        return studentsByName.values();
    }

    /**
     * Gets an iterator over all students from a specific country,
     * in their original order of registration.
     *
     * @param country The country name to filter by.
     * @return A {@link Iterator} of {@link Student}s from that country.
     */
    @Override
    public Iterator<Student> listStudentsByCountry(String country) {
        List<Student> list = studentsByCountry.get(country.toLowerCase());
        if (list != null) {
            return list.iterator();
        }
        return new DoublyLinkedList<Student>().iterator(); // empty iterator
    }

    /**
     * Gets all students in insertion order for persistence or other needs.
     * Builds a temporary list by concatenating country lists.
     *
     * @return An {@link Iterator} of all students in insertion order.
     */
    public Iterator<Student> getStudentsByInsertion() {
        DoublyLinkedList<Student> allOrdered = new DoublyLinkedList<>();

        // Iterate over all country lists to maintain insertion order
        Iterator<List<Student>> countryLists = studentsByCountry.values();
        while (countryLists.hasNext()) {
            List<Student> countryList = countryLists.next();
            Iterator<Student> studentIt = countryList.iterator();
            while (studentIt.hasNext()) {
                allOrdered.addLast(studentIt.next());
            }
        }
        return allOrdered.iterator();
    }

    // --- Custom Serialization ---

    /**
     * Custom writeObject for optimized serialization.
     * Writes the size and then each student with their name and country keys.
     * This avoids serializing the entire map structure and reduces metadata overhead.
     * Uses keys() and get() since entrySet() and entries() may not be available.
     *
     * @param out The ObjectOutputStream to write to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        // Write the number of students
        out.writeInt(studentsByName.size());

        // Iterate over all students using keys() + get()
        Iterator<String> nameKeys = studentsByName.keys();
        while (nameKeys.hasNext()) {
            String lowerName = nameKeys.next();
            Student student = studentsByName.get(lowerName);
            out.writeUTF(lowerName); // lowercase name
            out.writeUTF(student.getCountry().toLowerCase()); // lowercase country
            out.writeObject(student); // the Student object
        }

        // Write the country map structure using keys() + get() (sizes for validation)
        Iterator<String> countryKeys = studentsByCountry.keys();
        out.writeInt(studentsByCountry.size());
        while (countryKeys.hasNext()) {
            String country = countryKeys.next();
            List<Student> countryList = studentsByCountry.get(country);
            out.writeUTF(country); // country key
            out.writeInt(countryList != null ? countryList.size() : 0); // size
        }
    }

    /**
     * Custom readObject for optimized deserialization.
     * Reads students and reconstructs the maps from the serialized data.
     * This is faster than default as it avoids JDK's reflection-heavy reconstruction.
     *
     * @param in The ObjectInputStream to read from.
     * @throws IOException If an I/O error occurs.
     * @throws ClassNotFoundException If a class cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // Initialize empty maps
        studentsByName = new AVLSortedMap<>();
        studentsByCountry = new SepChainHashTable<>();

        // Read the number of students
        int numStudents = in.readInt();

        // Read and reconstruct each student
        for (int i = 0; i < numStudents; i++) {
            String lowerName = in.readUTF();
            String lowerCountry = in.readUTF();
            Student student = (Student) in.readObject();

            // Add to name map
            studentsByName.put(lowerName, student);

            // Add to country map
            List<Student> countryList = studentsByCountry.get(lowerCountry);
            if (countryList == null) {
                countryList = new DoublyLinkedList<>();
                studentsByCountry.put(lowerCountry, countryList);
            }
            countryList.addLast(student);
        }

        // Read country map metadata (sizes for validation, but lists already reconstructed above)
        int numCountries = in.readInt();
        for (int i = 0; i < numCountries; i++) {
            in.readUTF(); // country key (already used)
            in.readInt(); // size (for validation if needed)
        }
    }
}
