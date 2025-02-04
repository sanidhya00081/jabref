import org.jabref.gui.externalfiles.FindUnlinkedFilesAction;
import org.jabref.gui.util.OptionalObjectProperty;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.gui.StateManager;
import org.jabref.gui.DialogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FindUnlinkedFilesActionTest {

    @Mock
    private DialogService dialogService;

    @Mock
    private StateManager stateManager;

    @Mock
    private BibDatabaseContext databaseContext;

    // Mocking the OptionalObjectProperty to avoid directly instantiating it
    @Mock
    private OptionalObjectProperty<BibDatabaseContext> activeDatabaseProperty;

    @InjectMocks
    private FindUnlinkedFilesAction action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setting up mock for active database property (instead of directly instantiating OptionalObjectProperty)
        when(stateManager.activeDatabaseProperty()).thenReturn(activeDatabaseProperty);
        when(activeDatabaseProperty.get()).thenReturn(Optional.of(databaseContext));  // Ensures that get() returns a valid context

        // mocking the active database method to return a valid database context
        when(stateManager.getActiveDatabase()).thenReturn(Optional.of(databaseContext));

        //Correcting constructor call for action
        action = new FindUnlinkedFilesAction(dialogService, stateManager); // Correct order of arguments
    }

    @Test
    void testExecute() {
        // Ensuring that the execute method runs without exceptions
        action.execute();
    }
}
