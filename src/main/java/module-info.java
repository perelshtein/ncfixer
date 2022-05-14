module com.github.perelshtein.ncfixer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;

    opens com.github.perelshtein.ncfixer to javafx.fxml;
    exports com.github.perelshtein.ncfixer;
}