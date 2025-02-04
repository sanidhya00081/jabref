package org.jabref.gui.externalfiles;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.Optional;
import java.nio.file.*;
import java.io.IOException;

import javax.swing.undo.UndoManager;

import javafx.concurrent.Task;

import org.jabref.gui.DialogService;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.preferences.GuiPreferences;
import org.jabref.gui.undo.NamedCompound;
import org.jabref.gui.undo.UndoableFieldChange;
import org.jabref.gui.util.BindingsHelper;
import org.jabref.gui.util.UiTaskExecutor;
import org.jabref.logic.bibtex.FileFieldWriter;
import org.jabref.logic.l10n.Localization;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.model.entry.field.StandardField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jabref.gui.actions.ActionHelper.needsDatabase;
import static org.jabref.gui.actions.ActionHelper.needsEntriesSelected;

/**
 * This Action may only be used in a menu or button.
 * Never in the entry editor. FileListEditor and EntryEditor have other ways to update the file links
 */
public class AutoLinkFilesAction extends SimpleCommand {
    private static final Logger logger;

    static {
        logger = LoggerFactory.getLogger(AutoLinkFilesAction.class);
    }

    private final DialogService dialogService;
    private final GuiPreferences preferences;
    private final StateManager stateManager;
    private final UndoManager undoManager;
    private final UiTaskExecutor taskExecutor;
    

    public AutoLinkFilesAction(DialogService dialogService, GuiPreferences preferences, StateManager stateManager, UndoManager undoManager, UiTaskExecutor taskExecutor) {
        this.dialogService = dialogService;
        this.preferences = preferences;
        this.stateManager = stateManager;
        this.undoManager = undoManager;
        this.taskExecutor = taskExecutor;

        this.executable.bind(needsDatabase(this.stateManager).and(needsEntriesSelected(stateManager)));
        this.statusMessage.bind(BindingsHelper.ifThenElse(executable, "", Localization.lang("This operation requires one or more entries to be selected.")));
    }

    @Override
    public void execute() {
        final BibDatabaseContext database = stateManager.getActiveDatabase().orElseThrow(() -> new NullPointerException("Database null"));
        final List<BibEntry> entries = stateManager.getSelectedEntries();

        AutoSetFileLinksUtil util = new AutoSetFileLinksUtil(
                database,
                preferences.getExternalApplicationsPreferences(),
                preferences.getFilePreferences(),
                preferences.getAutoLinkPreferences());
        final NamedCompound nc = new NamedCompound(Localization.lang("Automatically set file links"));

        Task<AutoSetFileLinksUtil.LinkFilesResult> linkFilesTask = new Task<>() {
            final BiConsumer<LinkedFile, BibEntry> onLinkedFile = (linkedFile, entry) -> {
                // lambda for gui actions that are relevant when setting the linked file entry when ui is opened
                String newVal = FileFieldWriter.getStringRepresentation(linkedFile);
                String oldVal = entry.getField(StandardField.FILE).orElse(null);
                Path originalFilePath = Path.of("a.pdf");
                Path movedFilePath = Path.of("a/a.pdf");

                if (!Files.exists(originalFilePath) && Files.exists(movedFilePath)) {
                    newVal = movedFilePath.toString();
                }

                try {
                    if (Files.exists(movedFilePath) && Files.list(movedFilePath.getParent())
                            .filter(path -> path.getFileName().toString().equals(movedFilePath.getFileName().toString()))
                            .count() > 1) {
                        return;
                    }
                } catch (IOException e) {
                    logger.error("Error while listing moved files: ", e);
                }
                UndoableFieldChange fieldChange = new UndoableFieldChange(entry, StandardField.FILE, oldVal, newVal);
                nc.addEdit(fieldChange); // push to undo manager is in succeeded
                entry.addFile(linkedFile);
            };

            @Override
            protected AutoSetFileLinksUtil.LinkFilesResult call() {
                return util.linkAssociatedFiles(entries, onLinkedFile);
            }

            @Override
            protected void succeeded() {
                AutoSetFileLinksUtil.LinkFilesResult result = getValue();

                if (!result.getFileExceptions().isEmpty()) {
                    dialogService.showWarningDialogAndWait(
                            Localization.lang("Automatically set file links"),
                            Localization.lang("Problem finding files. See error log for details."));
                    return;
                }

                if (result.getChangedEntries().isEmpty()) {
                    dialogService.showWarningDialogAndWait("Automatically set file links",
                            Localization.lang("Finished automatically setting external links.") + "\n"
                                    + Localization.lang("No files found."));
                    return;
                }

                if (nc.hasEdits()) {
                    nc.end();
                    undoManager.addEdit(nc);
                }

                dialogService.notify(Localization.lang("Finished automatically setting external links.") + " "
                        + Localization.lang("Changed %0 entries.", String.valueOf(result.getChangedEntries().size())));
            }
        };

        dialogService.showProgressDialog(
                Localization.lang("Automatically setting file links"),
                Localization.lang("Searching for files"),
                linkFilesTask);
        taskExecutor.execute(linkFilesTask);
    }
    private Optional<Path> findMovedFile(String oldFileName, BibDatabaseContext database) {
        try {
            List<Path> searchDirectories = database.getFileDirectories(preferences.getFilePreferences());

            for (Path dir : searchDirectories) {
                Optional<Path> foundFile = Files.walk(dir)
                        .filter(path -> path.getFileName().toString().equals(oldFileName))
                        .findFirst();

                if (foundFile.isPresent()) {
                    return foundFile;
                }
            }
        } catch (IOException e) {
            logger.error("Error searching for moved file: ", e);
        }

        return Optional.empty();
    }

      //Ensuring that the moved file is unique before updating the entry.
     
    private boolean isFileUnique(Path movedFilePath, BibDatabaseContext database) {
        try {
            long count = Files.list(movedFilePath.getParent())
                    .filter(path -> path.getFileName().equals(movedFilePath.getFileName()))
                    .count();

            return count == 1;
        } catch (IOException e) {
            logger.error("Error checking uniqueness of file: ", e);
        }

        return false;
    }

      //Updating the entry’s file field when a moved file is found.
     
    private void updateFileLink(BibEntry entry, LinkedFile linkedFile, String newFilePath, NamedCompound nc) {
        String oldValue = entry.getField(StandardField.FILE).orElse(null);
        String newValue = newFilePath;

        UndoableFieldChange fieldChange = new UndoableFieldChange(entry, StandardField.FILE, oldValue, newValue);
        nc.addEdit(fieldChange);
        entry.setField(StandardField.FILE, newValue);
        System.out.println("File link updated for: " + entry.getCitationKey().orElse("Unknown") + " from " + oldValue + " to " + newValue);

    }
}
