package org.omnetpp.scave.editors;

import static org.omnetpp.common.util.Triplet.triplet;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IHyperlink;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.IOConsoleOutputStream;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.MarkerAnnotation;
import org.omnetpp.common.Debug;
import org.omnetpp.common.canvas.LargeScrollableCanvas;
import org.omnetpp.common.canvas.ZoomableCachingCanvas;
import org.omnetpp.common.canvas.ZoomableCanvasMouseSupport;
import org.omnetpp.common.util.DelayedJob;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.common.util.Triplet;
import org.omnetpp.scave.ScaveImages;
import org.omnetpp.scave.ScavePlugin;
import org.omnetpp.scave.actions.AddVectorOperationAction;
import org.omnetpp.scave.actions.ChartMouseModeAction;
import org.omnetpp.scave.actions.ClosePageAction;
import org.omnetpp.scave.actions.CopyChartImageToClipboardAction;
import org.omnetpp.scave.actions.EditChartPropertiesAction;
import org.omnetpp.scave.actions.ExportToSVGAction;
import org.omnetpp.scave.actions.RefreshChartAction;
import org.omnetpp.scave.actions.SaveTempChartAction;
import org.omnetpp.scave.actions.ToggleShowSourceAction;
import org.omnetpp.scave.actions.ZoomChartAction;
import org.omnetpp.scave.editors.ui.ChartPage;
import org.omnetpp.scave.editors.ui.FormEditorPage;
import org.omnetpp.scave.model.Chart;
import org.omnetpp.scave.model.Chart.ChartType;
import org.omnetpp.scave.model.IModelChangeListener;
import org.omnetpp.scave.model.ModelChangeEvent;
import org.omnetpp.scave.model.commands.CommandStack;
import org.omnetpp.scave.pychart.PlotWidget;
import org.omnetpp.scave.pychart.PythonCallerThread.ExceptionHandler;
import org.omnetpp.scave.pychart.PythonOutputMonitoringThread.IOutputListener;
import org.omnetpp.scave.python.BackAction;
import org.omnetpp.scave.python.ChartViewerBase;
import org.omnetpp.scave.python.ExportAction;
import org.omnetpp.scave.python.ForwardAction;
import org.omnetpp.scave.python.GotoChartDefinitionAction;
import org.omnetpp.scave.python.HomeAction;
import org.omnetpp.scave.python.InteractAction;
import org.omnetpp.scave.python.KillPythonProcessAction;
import org.omnetpp.scave.python.MatplotlibChartViewer;
import org.omnetpp.scave.python.MatplotlibChartViewer.IStateChangeListener;
import org.omnetpp.scave.python.NativeChartViewer;
import org.omnetpp.scave.python.PanAction;
import org.omnetpp.scave.python.ToggleAutoUpdateAction;
import org.omnetpp.scave.python.ZoomAction;
import org.python.pydev.editor.PyEdit;
import org.python.pydev.shared_core.callbacks.ICallbackListener;
import org.python.pydev.shared_ui.editor_input.PydevFileEditorInput;

//TODO ScaveEditor remains dirty after hitting Ctrl+S in edited script editor
//TODO vector operations via chart properties not text insertion
//TODO on errors, display stack trace somewhere (in log?)
//TODO @title not used by chart

public class ChartScriptEditor extends PyEdit {
    Chart chart;
    Chart originalChart;
    ScaveEditor scaveEditor;

    FormEditorPage formEditor;
    SashForm sashForm;
    Composite sourceEditorContainer;

    NativeChartViewer nativeChartViewer;
    MatplotlibChartViewer matplotlibChartViewer;

    ChangeListener changeListener;
    ChartScriptEditorInput editorInput;

    IOConsole console;
    IOConsoleOutputStream outputStream;
    IOConsoleOutputStream errorStream;

    ChartScriptDocumentProvider documentProvider;
    protected CommandStack commandStack = new CommandStack("ChartPage");

	IMarker errorMarker;
    MarkerAnnotation errorMarkerAnnotation;

    SaveTempChartAction saveTempChartAction;
    GotoChartDefinitionAction gotoChartDefinitionAction;

    ExportAction exportAction = new ExportAction();

    InteractAction interactAction = new InteractAction();
    PanAction panAction = new PanAction();
    ZoomAction zoomAction = new ZoomAction();

    HomeAction homeAction = new HomeAction();
    BackAction backAction = new BackAction();
    ForwardAction forwardAction = new ForwardAction();

    ZoomChartAction zoomToFitAction = new ZoomChartAction(true, true, 0.0);
    ZoomChartAction zoomInHorizAction = new ZoomChartAction(true, false, 2.0);
    ZoomChartAction zoomOutHorizAction = new ZoomChartAction(true, false, 1 / 2.0);

    ZoomChartAction zoomInVertAction = new ZoomChartAction(false, true, 2.0);
    ZoomChartAction zoomOutVertAction = new ZoomChartAction(false, true, 1 / 2.0);

    KillPythonProcessAction killAction;

    boolean scriptNotYetExecuted = true;

    ToggleShowSourceAction toggleShowSourceAction = new ToggleShowSourceAction();
    boolean showSource = false;

    private static final int CHART_SCRIPT_EXECUTION_DELAY_MS = 2000;
    private boolean autoUpdateChart = true;
    ToggleAutoUpdateAction toggleAutoUpdateAction;

    // if the document has changed since last saving (independent of the Chart object in the model)
    boolean scriptChangedFlag = false;

    private class StackFrameHyperlink implements IHyperlink {
        private final String file;
        private final int lineNumber;

        public StackFrameHyperlink(String file, int lineNumber) {
            this.file = file;
            this.lineNumber = lineNumber;
        }

        @Override
        public void linkExited() {
        }

        @Override
        public void linkEntered() {
        }

        @Override
        public void linkActivated() {
            showSourceForStackFrame(file, lineNumber);
        }
    }

    public class PatternMatchListener implements IPatternMatchListener {
        @Override
        public void connect(TextConsole console) {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public void matchFound(PatternMatchEvent event) {
            try {
                IDocument document = console.getDocument();
                String fileAndLine = document.get(event.getOffset(), event.getLength());
                Matcher matcher = Pattern.compile("File \"(.*?)\", line (\\d+)").matcher(fileAndLine);
                Assert.isTrue(matcher.matches());
                if (matcher.matches()) {
                    String fileName = matcher.group(1);
                    int lineNumber = Integer.parseInt(matcher.group(2));
                    console.addHyperlink(new StackFrameHyperlink(fileName, lineNumber), event.getOffset(), event.getLength());
                }
            }
            catch (BadLocationException e) {
                ScavePlugin.logError(e);
            }
        }

        @Override
        public String getPattern() {
            return "File \".*?\", line \\d+";
        }

        @Override
        public int getCompilerFlags() {
            return 0;
        }

        @Override
        public String getLineQualifier() {
            return null;
        }

    }

    private final DelayedJob rerunChartScriptJob = new DelayedJob(CHART_SCRIPT_EXECUTION_DELAY_MS) {
        @Override
        public void run() {
            refreshChart();
        }
    };

    private class ChangeListener implements IModelChangeListener, IDocumentListener {

        @Override
        public void documentAboutToBeChanged(DocumentEvent event) {
            // no-op
        }

        @Override
        public void documentChanged(DocumentEvent event) {
            scriptChangedFlag = true;
            firePropertyChange(ScaveEditor.PROP_DIRTY);
            if (autoUpdateChart)
                rerunChartScriptJob.restartTimer();
        }

        @Override
        public void modelChanged(ModelChangeEvent event) {
            if (autoUpdateChart)
                rerunChartScriptJob.restartTimer();
        }
    }

    ChartScriptEditor(ScaveEditor scaveEditor, Chart chart) {
        this.chart = chart;
        this.originalChart = (Chart)chart.dup();
        this.scaveEditor = scaveEditor;


        this.documentProvider = new ChartScriptDocumentProvider();

        console = new IOConsole("Chart script output of " + getChartName(), null);
        IConsoleManager consoleManager = ConsolePlugin.getDefault().getConsoleManager();
        consoleManager.addConsoles(new IConsole[] { console });
        outputStream = console.newOutputStream();
        errorStream = console.newOutputStream();
        errorStream.setColor(new Color(Display.getCurrent(), 220, 10, 10));

        console.addPatternMatchListener(new PatternMatchListener());

        onCreatePartControl.registerListener(new ICallbackListener<Composite>() {

            @Override
            public Object call(Composite obj) {

                formEditor = new ChartPage(obj, SWT.NONE, scaveEditor, ChartScriptEditor.this);
                Composite content = formEditor.getContent();

                saveTempChartAction = new SaveTempChartAction();
                gotoChartDefinitionAction = new GotoChartDefinitionAction();

                formEditor.addToToolbar(saveTempChartAction);
                formEditor.addToToolbar(gotoChartDefinitionAction);

                formEditor.setToolbarActionVisible(saveTempChartAction, chart.isTemporary());
                formEditor.setToolbarActionVisible(gotoChartDefinitionAction, !chart.isTemporary());

                formEditor.addSeparatorToToolbar();

                formEditor.addToToolbar(toggleShowSourceAction);
                formEditor.addToToolbar(new EditChartPropertiesAction());

                toggleAutoUpdateAction = new ToggleAutoUpdateAction();
                toggleAutoUpdateAction.setChecked(true);

                if (chart.getType() == ChartType.MATPLOTLIB) {
                    formEditor.addSeparatorToToolbar();
                    interactAction.setChecked(true);
                    formEditor.addToToolbar(interactAction);
                    formEditor.addToToolbar(panAction);
                    formEditor.addToToolbar(zoomAction);
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(homeAction);
                    formEditor.addToToolbar(backAction);
                    formEditor.addToToolbar(forwardAction);
                    formEditor.addSeparatorToToolbar();
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(toggleAutoUpdateAction);
                    formEditor.addToToolbar(new RefreshChartAction());
                    formEditor.addToToolbar(killAction = new KillPythonProcessAction());
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(new CopyChartImageToClipboardAction());
                    formEditor.addToToolbar(exportAction);
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(new ClosePageAction());
                }
                else {
                    formEditor.addSeparatorToToolbar();
                    ChartMouseModeAction panAction = new ChartMouseModeAction(ZoomableCanvasMouseSupport.PAN_MODE);
                    formEditor.addToToolbar(panAction);
                    panAction.setChecked(true);
                    formEditor.addToToolbar(new ChartMouseModeAction(ZoomableCanvasMouseSupport.ZOOM_MODE));
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(zoomToFitAction);
                    formEditor.addToToolbar(zoomInHorizAction);
                    formEditor.addToToolbar(zoomOutHorizAction);
                    formEditor.addToToolbar(zoomInVertAction);
                    formEditor.addToToolbar(zoomOutVertAction);
                    formEditor.addSeparatorToToolbar();
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(toggleAutoUpdateAction);
                    formEditor.addToToolbar(new RefreshChartAction());
                    formEditor.addToToolbar(killAction = new KillPythonProcessAction());
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(new CopyChartImageToClipboardAction());
                    formEditor.addToToolbar(new ExportToSVGAction());
                    formEditor.addSeparatorToToolbar();
                    formEditor.addToToolbar(new ClosePageAction());
                }

                formEditor.setFormTitle(getChartName());
                GridLayout layout = new GridLayout(1, true);
                layout.marginWidth = 0;
                layout.marginHeight = 0;
                content.setLayout(layout);

                sashForm = new SashForm(content, SWT.NONE);
                sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

                formEditor.getToolbar().layout();
                formEditor.layout();
                content.layout();
                obj.layout();

                return sashForm;
            }
        });

        onAfterCreatePartControl.registerListener(new ICallbackListener<ISourceViewer>() {

            @Override
            public Object call(ISourceViewer obj) {

                Assert.isTrue(sashForm.getChildren().length == 1 && sashForm.getChildren()[0] instanceof Composite);
                sourceEditorContainer = (Composite) (sashForm.getChildren()[0]);

                final IActionBars actionBars = getEditorSite().getActionBars();
                final IAction undoActionHandler = actionBars.getGlobalActionHandler(ActionFactory.UNDO.getId());
                final IAction redoActionHandler = actionBars.getGlobalActionHandler(ActionFactory.REDO.getId());


                obj.getTextWidget().addFocusListener(new FocusListener() {

                    @Override
                    public void focusLost(FocusEvent e) {
                        final IActionBars actionBars = getEditorSite().getActionBars();
                        actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(), scaveEditor.getActions().undoAction);
                        actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(), scaveEditor.getActions().redoAction);
                        actionBars.updateActionBars();
                    }

                    @Override
                    public void focusGained(FocusEvent e) {
                        actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(), undoActionHandler);
                        actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(), redoActionHandler);
                        actionBars.updateActionBars();
                    }
                });

                IOutputListener outputListener = (output, err) -> {
                    Display.getDefault().asyncExec(() -> {
                        try {
                            if (err)
                                errorStream.write(output);
                            else
                                outputStream.write(output);
                        }
                        catch (IOException e) {
                            ScavePlugin.logError(e);
                        }
                    });
                };

                IStateChangeListener stateChangeListener = new IStateChangeListener() {

                    @Override
                    public void pythonProcessLivenessChanged(boolean alive) {
                        Display.getDefault().syncExec(() -> {
                            if (!isDisposed())
                                updateActions();
                        });
                    }

                    @Override
                    public void activeActionChanged(String action) {
                        updateActiveAction(action);
                    }
                };

                @SuppressWarnings("unchecked")
                Triplet<String, String, String>[] vectorOpData = new Triplet[] {
                    triplet("Mean", "mean", ""),
                    triplet("Aggregator", "aggregator, function=average", "Possible parameter values: 'sum', 'average', 'count', 'maximum', 'minimum'"),
                    triplet("Merger", "merger", ""),
                    triplet("Sum", "sum", ""),
                    triplet("Add constant", "add, c=100", "Adds a constant to all values in the vectors"),
                    triplet("Compare with threshold", "compare, threshold=9000, less=-1, equal=0, greater=1", "The last three parameters are all optional"),
                    triplet("Crop in time", "crop, from_time=10, to_time=100)", "The time values are in seconds"),
                    triplet("Difference", "difference", "The difference of each value compared to the previous"),
                    triplet("Difference quotient", "diffquot", "The difference quotient at each value"),
                    triplet("Divide by constant", "divide_by, a=1000", "Divides every value in each vector by a constant"),
                    triplet("Divide by time", "divtime", ""),
                    triplet("Expression", "expression, expression='y + (t - tprev) * 100'", "The following identifiers are available: t, y, tprev, yprev"),
                    triplet("Integrate", "integrate, interpolation='linear'", "Possible parameter values: 'sample-hold', 'backward-sample-hold', 'linear'"),
                    triplet("Linear trend", "lineartrend, a=0.5", "Add a linear trend to the values, with the given steepness"),
                    triplet("Modulo", "modulo, m=256.0", "Floating point remainder with the given divisor"),
                    triplet("Moving average", "movingavg, alpha=0.1", "Moving average using the given smoothing constant in range (0.0, 1.0]"),
                    triplet("Multiply by constant", "multiply_by, a=2", "You can change the multiplier constant"),
                    triplet("Remove repeating values", "removerepeats", "Removes consequtive equal values"),
                    triplet("Sliding window average", "slidingwinavg, window_size=10", "The average of the last window_size values"),
                    triplet("Subtract first value", "subtractfirstval", "Subtracts the first value from all values"),
                    triplet("Time average", "timeavg, interpolation='linear'", "Average over time (integral divided by time), possible parameter values: 'sample-hold', 'backward-sample-hold', 'linear'"),
                    triplet("Time difference", "timediff", "The elapsed time (delta) since the previous value"),
                    triplet("Time shift", "timeshift, dt=100", "Shifts all values with the given time delta (in seconds)"),
                    triplet("Time to serial", "timetoserial", "Replaces all times with a serial number: 0, 1, 2, ..."),
                    triplet("Time window average", "timewinavg, window_size=0.1", "Average of all values in every window_size long interval (in seconds)"),
                    triplet("Window average", "winavg, window_size=10", "Average of every window_size long batch of values"),
                };


                IMenuManager applySubmenuManager = new MenuManager("Apply...", ScavePlugin.getImageDescriptor(ScaveImages.IMG_ETOOL16_APPLY), null);

                for (Triplet<String, String, String> t : vectorOpData)
                    applySubmenuManager.add(new AddVectorOperationAction(t.first, "apply:" + t.second, t.third));

                IMenuManager computeSubmenuManager = new MenuManager("Compute...", ScavePlugin.getImageDescriptor(ScaveImages.IMG_ETOOL16_COMPUTE), null);

                for (Triplet<String, String, String> t : vectorOpData)
                    computeSubmenuManager.add(new AddVectorOperationAction(t.first, "compute:" + t.second, t.third));


                ScaveEditorActions actions = scaveEditor.getActions();

                if (chart.getType() == ChartType.MATPLOTLIB) {
                    matplotlibChartViewer = new MatplotlibChartViewer(scaveEditor.processPool, chart, scaveEditor.getResultFileManager(), sashForm);

                    matplotlibChartViewer.addOutputListener(outputListener);
                    matplotlibChartViewer.addStateChangeListener(stateChangeListener);

                    MenuManager manager = new MenuManager();

                    manager.add(new GotoChartDefinitionAction());
                    manager.add(new Separator());

                    manager.add(toggleShowSourceAction);
                    manager.add(new EditChartPropertiesAction());
                    manager.add(new Separator());

                    manager.add(actions.undoAction);
                    manager.add(actions.redoAction);
                    manager.add(new Separator());

                    // TODO: buttons to control RLE and halfres interaction? Or maybe only in a
                    // settings dialog somewhere?

                    MenuManager navigationMenu = new MenuManager("Navigation");
                    navigationMenu.add(interactAction);
                    navigationMenu.add(panAction);
                    navigationMenu.add(zoomAction);

                    navigationMenu.add(new Separator());

                    navigationMenu.add(homeAction);
                    navigationMenu.add(backAction);
                    navigationMenu.add(forwardAction);

                    manager.add(navigationMenu);
                    manager.add(new Separator());

                    manager.add(applySubmenuManager);
                    manager.add(computeSubmenuManager);
                    manager.add(new Separator());

                    manager.add(toggleAutoUpdateAction);
                    manager.add(new RefreshChartAction());
                    manager.add(killAction);
                    manager.add(new Separator());

                    manager.add(new CopyChartImageToClipboardAction());
                    manager.add(exportAction);

                    PlotWidget plotWidget = matplotlibChartViewer.getPlotWidget();
                    plotWidget.setMenu(manager.createContextMenu(plotWidget));
                }
                else {
                    nativeChartViewer = new NativeChartViewer(scaveEditor.getPythonProcessPool(), chart, scaveEditor.getResultFileManager(), sashForm);

                    nativeChartViewer.addOutputListener(outputListener);
                    nativeChartViewer.addStateChangeListener(stateChangeListener);

                    nativeChartViewer.getChartViewer().addPropertyChangeListener(new IPropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent event) {
                            if (event.getProperty() == LargeScrollableCanvas.PROP_VIEW_X
                                    || event.getProperty() == LargeScrollableCanvas.PROP_VIEW_Y
                                    || event.getProperty() == ZoomableCachingCanvas.PROP_ZOOM_X
                                    || event.getProperty() == ZoomableCachingCanvas.PROP_ZOOM_Y) {
                                updateActions();
                            }
                        }
                    });

                    IMenuManager zoomSubmenuManager = new MenuManager("Zoom", ScavePlugin.getImageDescriptor(ScaveImages.IMG_ETOOL16_ZOOM), null);
                    zoomSubmenuManager.add(zoomInHorizAction);
                    zoomSubmenuManager.add(zoomOutHorizAction);
                    zoomSubmenuManager.add(zoomInVertAction);
                    zoomSubmenuManager.add(zoomOutVertAction);
                    zoomSubmenuManager.add(new Separator());
                    zoomSubmenuManager.add(zoomToFitAction);

                    MenuManager manager = new MenuManager();

                    manager.add(new GotoChartDefinitionAction());
                    manager.add(toggleShowSourceAction);
                    manager.add(new EditChartPropertiesAction());
                    manager.add(new Separator());

                    manager.add(actions.undoAction);
                    manager.add(actions.redoAction);
                    manager.add(new Separator());

                    if (chart.getType() == ChartType.LINE) {

                        manager.add(applySubmenuManager);
                        manager.add(computeSubmenuManager);
                        manager.add(new Separator());
                    }

                    manager.add(zoomSubmenuManager);

                    manager.add(new Separator());
                    manager.add(toggleAutoUpdateAction);
                    manager.add(new RefreshChartAction());
                    manager.add(killAction);

                    manager.add(new Separator());
                    manager.add(new CopyChartImageToClipboardAction());
                    manager.add(new ExportToSVGAction());

                    nativeChartViewer.getChartViewer().setMenu(manager.createContextMenu(nativeChartViewer.getChartViewer()));

                    actions.registerChart(nativeChartViewer.getChartViewer());
                }


                getChartViewer().getWidget().addFocusListener(new FocusListener() {

                    @Override
                    public void focusLost(FocusEvent e) {
                    }

                    @Override
                    public void focusGained(FocusEvent e) {
                        final IActionBars actionBars = getEditorSite().getActionBars();
                        actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(), scaveEditor.getActions().undoAction);
                        actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(), scaveEditor.getActions().redoAction);
                        actionBars.updateActionBars();
                    }
                });


                sashForm.layout();

                if (chart.getScript() == null || chart.getScript().isEmpty())
                    showSource = true;

                setShowSource(showSource);

                return obj;
            }

        });

        editorInput = new ChartScriptEditorInput(chart);

        changeListener = new ChangeListener();
        getDocumentProvider().getDocument(editorInput).addDocumentListener(changeListener);
        chart.addListener(changeListener);
    }

    public ScaveEditor getScaveEditor() {
        return scaveEditor;
    }

    public CommandStack getCommandStack() {
		return commandStack;
	}

    public void runChartScript() {
        scriptNotYetExecuted = false;

        Display.getDefault().syncExec(() -> {
            console.clearConsole();
            documentProvider.annotationModel.removeAnnotation(errorMarkerAnnotation);

            try {
                if (errorMarker != null)
                    errorMarker.delete();
            }
            catch (CoreException e1) {
                ScavePlugin.logError(e1);
            }
            errorMarker = null;

            Runnable afterRun = () -> {
                Display.getDefault().syncExec(() -> {
                    updateActions();
                });
            };

            ExceptionHandler errorHandler = (proc, e) -> {
                Display.getDefault().syncExec(() -> {
                    if (!proc.isKilledByUs()) {
                        try {
                            errorStream.write(tweakStacktrace(e.getMessage()));
                        } catch (IOException e1) {
                            ScavePlugin.logError(e);
                        }
                        annotatePythonException(e);
                        revealErrorAnnotation();
                    }
                });
            };

            getChartViewer().runPythonScript(getDocument().get(), scaveEditor.getAnfFileDirectory(), afterRun, errorHandler);
        });
    }

    private String tweakStacktrace(String msg) {
        // Tweak the exception message to remove stack frames related to Py4J.
        // Only tweak if the message conforms to the expected pattern, otherwise leave it alone.
        String expectedFirstLine = "An exception was raised by the Python Proxy. Return Message: Traceback (most recent call last):";
        String replacementFirstLine = "An error occurred. Traceback (most recent call last):";
        String startOfFirstRelevantFrame = "  File \"<string>\", line ";

        String pattern = "(?s)" + Pattern.quote(expectedFirstLine) + "\\n.*?\\n" + Pattern.quote(startOfFirstRelevantFrame);
        return msg.replaceFirst(pattern, replacementFirstLine + "\n" + startOfFirstRelevantFrame);
    }

    private void annotatePythonException(Exception e) {
        String msg = e.getMessage();

        String problemMessage = null;

        IDocument doc = getDocument();

        int offset = 0;
        int length = doc.getLength();
        int line = 0;

        String[] parts = msg.split("File \"<string>\", line ", 2);

        if (parts.length == 0)
            problemMessage = "Unknown error.";
        else if (parts.length == 1)
            problemMessage = parts[0].trim();
        else {
            String[] parts2 = parts[1].split("(,|\n)", 2);

            line = Integer.parseInt(parts2[0]);

            try {
                offset = doc.getLineOffset(line - 1);
                length = doc.getLineLength(line - 1);
            }
            catch (BadLocationException exc) {
                // ignore
            }

            if (msg.contains("py4j.protocol.Py4JJavaError")) {
                problemMessage = StringUtils.substringAfter(msg, "py4j.protocol.Py4JJavaError: ");
                problemMessage = StringUtils.substringAfterLast(problemMessage, ": ");
                problemMessage = StringUtils.substringBefore(problemMessage, "\n");
            } else
                problemMessage = StringUtils.substringAfterLast(msg.trim(), "\n");
        }

        try {
            documentProvider.annotationModel.removeAnnotation(errorMarkerAnnotation);
            if (errorMarker != null)
                errorMarker.delete();

            errorMarker = scaveEditor.getInputFile().createMarker(IMarker.PROBLEM);
            errorMarker.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            errorMarker.setAttribute(IMarker.TRANSIENT, true);
            errorMarker.setAttribute(IMarker.MESSAGE, problemMessage);
            errorMarker.setAttribute(IMarker.LINE_NUMBER, line);
            errorMarker.setAttribute(IMarker.SOURCE_ID, Integer.toString(scaveEditor.getAnalysis().getCharts().getCharts().indexOf(chart)));

            errorMarkerAnnotation = new MarkerAnnotation(errorMarker);
            documentProvider.annotationModel.addAnnotation(errorMarkerAnnotation, new Position(offset, length));
        }
        catch (CoreException e1) {
        }
    }

    public void updateActiveAction(String action) {
        if (action == null)
            action = "";
        interactAction.setChecked(action.equals(""));
        zoomAction.setChecked(action.equalsIgnoreCase("zoom"));
        panAction.setChecked(action.equalsIgnoreCase("pan"));
    }

    public void updateActions() {
        formEditor.setToolbarActionVisible(saveTempChartAction, chart.isTemporary());
        formEditor.setToolbarActionVisible(gotoChartDefinitionAction, !chart.isTemporary());

        saveTempChartAction.update();
        gotoChartDefinitionAction.update();

        zoomInHorizAction.update();
        zoomOutHorizAction.update();
        zoomInVertAction.update();
        zoomOutVertAction.update();

        killAction.update();

        interactAction.update();
        panAction.update();
        zoomAction.update();

        homeAction.update();
        backAction.update();
        forwardAction.update();

        exportAction.update();
    }

    public void setShowSource(boolean show) {
        toggleShowSourceAction.setChecked(show);
        sourceEditorContainer.setVisible(show);
        sashForm.layout();
        showSource = show;
    }

    public void revealErrorAnnotation() {
        setShowSource(true);

        Position pos = documentProvider.annotationModel.getPosition(errorMarkerAnnotation);
        if (pos != null)
            getSourceViewer().revealRange(pos.offset, pos.length);
    }

    public void gotoErrorAnnotation() {
        revealErrorAnnotation();
        Position pos = documentProvider.annotationModel.getPosition(errorMarkerAnnotation);
        getSourceViewer().setSelectedRange(pos.offset, 0);
    }

    public boolean isSourceShown() {
        return showSource;
    }

    public void setShowChart(boolean show) {
        if (nativeChartViewer != null)
            nativeChartViewer.setVisible(show);

        if (matplotlibChartViewer != null)
            matplotlibChartViewer.setVisible(show);

        sashForm.layout();
    }

    public boolean isPythonProcessAlive() {
        return getChartViewer().isAlive();
    }

    public void killPythonProcess() {
        getChartViewer().killPythonProcess();
    }

    public MatplotlibChartViewer getMatplotlibChartViewer() {
        return matplotlibChartViewer;
    }

    public NativeChartViewer getNativeChartViewer() {
        return nativeChartViewer;
    }

    public ChartViewerBase getChartViewer() {
        return (chart.getType() == ChartType.MATPLOTLIB) ? matplotlibChartViewer : nativeChartViewer;
    }

    @Override
    public IDocumentProvider getDocumentProvider() {
        return documentProvider;
    }

    public Chart getChart() {
        return chart;
    }

    public Chart getOriginalChart() {
        return originalChart;
    }

    public String getChartName() {
        return StringUtils.defaultIfEmpty(chart.getName(), "<unnamed>");
    }

    @Override
    public boolean isDirty() {
        return scriptChangedFlag || commandStack.isSaveNeeded();
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public void dispose() {
        if (isDisposed())
            return;

        getDocumentProvider().getDocument(editorInput).removeDocumentListener(changeListener);
        chart.removeListener(changeListener);

        if (matplotlibChartViewer != null)
            matplotlibChartViewer.dispose();

        if (nativeChartViewer != null)
            nativeChartViewer.dispose();

        try {
            outputStream.close();
        }
        catch (IOException e) {
        }

        IConsoleManager consoleManager = ConsolePlugin.getDefault().getConsoleManager();
        consoleManager.removeConsoles(new IConsole[] { console });

        documentProvider.annotationModel.removeAnnotation(errorMarkerAnnotation);

        try {
            if (errorMarker != null)
                errorMarker.delete();
        }
        catch (CoreException e) {
        }

        super.dispose();
    }

    public void refreshChart() {
        rerunChartScriptJob.cancel();
        runChartScript();
    }

    public boolean isAutoUpdateChart() {
        return autoUpdateChart;
    }

    public void setAutoUpdateChart(boolean autoUpdateChart) {
        toggleAutoUpdateAction.setChecked(autoUpdateChart);
        if (this.autoUpdateChart != autoUpdateChart) {
            this.autoUpdateChart = autoUpdateChart;
            if (autoUpdateChart)
                rerunChartScriptJob.runNow();
            else
                rerunChartScriptJob.cancel();
        }
    }

    @Override
    public void saveState(IMemento memento) {
        super.saveState(memento);
        memento.putBoolean("autoupdate", isAutoUpdateChart());
        memento.putBoolean("sourcevisible", isSourceShown());
        int[] weights = sashForm.getWeights();
        memento.putInteger("sashweight_left", weights[0]);
        memento.putInteger("sashweight_right", weights[1]);
    }

    @Override
    public void restoreState(IMemento memento) {
        super.restoreState(memento);

        setAutoUpdateChart(memento.getBoolean("autoupdate"));
        setShowSource(memento.getBoolean("sourcevisible"));

        int[] weights = new int[2];
        weights[0] = memento.getInteger("sashweight_left");
        weights[1] = memento.getInteger("sashweight_right");
        sashForm.setWeights(weights);
    }

    public void pageActivated() {
        if (scriptNotYetExecuted)
            runChartScript();
        scaveEditor.setSelection(new StructuredSelection(chart));
        console.activate();
    }

    public void gotoMarker(IMarker marker) {
        Assert.isTrue(this.errorMarker.equals(marker));
        gotoErrorAnnotation();
    }

    protected void showSourceForStackFrame(String filename, int lineNumber) {
        Debug.println("Opening location for: " + filename + ":" + lineNumber);
        try {
            if (filename.equals("<string>")) {
                getEditorSite().getPage().activate(scaveEditor);
                scaveEditor.showPage(formEditor);
                gotoLine(this, lineNumber);
            }
            else {
                IEditorInput editorInput = new PydevFileEditorInput(new File(filename));
                PyEdit pyedit = (PyEdit) IDE.openEditor(getSite().getPage(), editorInput, PyEdit.EDITOR_ID);
                gotoLine(pyedit, lineNumber);
            }
        }
        catch (PartInitException|BadLocationException e) {
            ScavePlugin.logError(e);
        }
    }

    protected void gotoLine(PyEdit pyedit, int lineNumber) throws BadLocationException {
            IDocument document = pyedit.getDocument();
            int offset = document.getLineOffset(lineNumber-1);
            getSourceViewer().setSelectedRange(offset, 0);
    }

    public void saved() {
        commandStack.saved();
        scriptChangedFlag = false;
    }
}
