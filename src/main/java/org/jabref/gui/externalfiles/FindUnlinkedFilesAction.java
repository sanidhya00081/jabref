package org.jabref.gui.externalfiles;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.model.entry.field.StandardField;

import static org.jabref.gui.actions.ActionHelper.needsDatabase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.net.URL;
import java.io.IOException;
import java.net.MalformedURLException;

public class FindUnlinkedFilesAction extends SimpleCommand {

    private final DialogService dialogService;
    private final StateManager stateManager;

    public FindUnlinkedFilesAction(DialogService dialogService, StateManager stateManager) {
        this.dialogService = dialogService;
        this.stateManager = stateManager;

        this.executable.bind(needsDatabase(this.stateManager));
    }

    @Override
    public void execute() {
        final BibDatabaseContext database = stateManager.getActiveDatabase().orElseThrow(() -> new NullPointerException("Database null"));
        final List<BibEntry> entries = stateManager.getSelectedEntries();

        for (BibEntry entry : entries) {
            Optional<String> fileField = entry.getField(StandardField.FILE);
            if (fileField.isPresent()) {
                String filePathString = fileField.get();
                Path filePath = Path.of(filePathString); 
                if (!Files.exists(filePath)) {
                    Path movedFilePath = findMovedFile(filePath);

                    if (movedFilePath != null) {
                        // Initialized LinkedFile with correct file path
                        LinkedFile linkedFile = new LinkedFile("", movedFilePath.toUri().toString(), "");

                        try {
                            //added the file if it's the only one with that name in the moved directory
                            if (Files.list(movedFilePath.getParent())
                                    .filter(path -> path.getFileName().toString().equals(movedFilePath.getFileName().toString()))
                                    .count() == 1) {
                                entry.addFile(linkedFile);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        dialogService.showCustomDialogAndWait(new UnlinkedFilesDialogView());
    }
    private Path findMovedFile(Path originalFilePath) {
        // Specifying a list of additional directories to check (e.g., user-defined directories or common move locations)
        List<Path> additionalSearchDirectories = getAdditionalSearchDirectories();

        for (Path dir : additionalSearchDirectories) {
            try {
                // Looking in each directory for files with the same name
                Optional<Path> movedFile = Files.list(dir)
                        .filter(path -> path.getFileName().toString().equals(originalFilePath.getFileName().toString()))
                        .findFirst();

                if (movedFile.isPresent()) {
                    return movedFile.get();
                }
            } catch (IOException e) {
                // To Handle exceptions (directory not accessible, etc.)
                e.printStackTrace();
            }
        }

        // Returned null if no moved file was found
        return null;
    }

    private List<Path> getAdditionalSearchDirectories() {
        // Returned a list of directories to search in, for now, it's empty
        // We can add directories where moved files are commonly located
        return new ArrayList<>();
    }
}
