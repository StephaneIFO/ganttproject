/*
GanttProject is an opensource project management tool.
Copyright (C) 2005-2011 GanttProject Team

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.sourceforge.ganttproject;

import biz.ganttproject.app.*;
import biz.ganttproject.ganttview.TaskFilterActionSet;
import biz.ganttproject.ganttview.TaskTable;
import biz.ganttproject.lib.fx.TreeTableCellsKt;
import biz.ganttproject.task.TaskActions;
import com.google.common.base.Suppliers;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.sourceforge.ganttproject.action.BaselineDialogAction;
import net.sourceforge.ganttproject.action.CalculateCriticalPathAction;
import net.sourceforge.ganttproject.action.GPAction;
import net.sourceforge.ganttproject.chart.Chart;
import net.sourceforge.ganttproject.gui.UIConfiguration;
import net.sourceforge.ganttproject.gui.UIFacade;
import net.sourceforge.ganttproject.gui.UIUtil;
import net.sourceforge.ganttproject.gui.view.ViewProvider;
import net.sourceforge.ganttproject.language.GanttLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

class GanttChartTabContentPanel extends ChartTabContentPanel implements ViewProvider {
  private final JComponent myGanttChart;
  private final UIFacade myWorkbenchFacade;
  private final CalculateCriticalPathAction myCriticalPathAction;
  private final BaselineDialogAction myBaselineAction;
  private final Supplier<TaskTable> myTaskTableSupplier;
  private final TaskActions myTaskActions;
  private final Function0<Unit> myInitializationCompleted;
  private JComponent myComponent;
  private TaskTable taskTable;
  private ViewComponents myViewComponents;

  GanttChartTabContentPanel(IGanttProject project, UIFacade workbenchFacade,
                            JComponent ganttChart, UIConfiguration uiConfiguration, Supplier<TaskTable> taskTableSupplier,
                            TaskActions taskActions, BarrierEntrance initializationPromise) {
    super(project, workbenchFacade, workbenchFacade.getGanttChart());
    myInitializationCompleted = initializationPromise.register("Task table inserted into the component tree");
    myTaskActions = taskActions;
    myTaskTableSupplier = taskTableSupplier;
    myWorkbenchFacade = workbenchFacade;
    myGanttChart = ganttChart;
    // FIXME KeyStrokes of these 2 actions are not working...
    myCriticalPathAction = new CalculateCriticalPathAction(project.getTaskManager(), uiConfiguration, workbenchFacade);
    myCriticalPathAction.putValue(GPAction.TEXT_DISPLAY, ContentDisplay.TEXT_ONLY);
    myBaselineAction = new BaselineDialogAction(project, workbenchFacade);
    myBaselineAction.putValue(GPAction.TEXT_DISPLAY, ContentDisplay.TEXT_ONLY);

    setImageHeight(() -> Double.valueOf(myViewComponents.getImage().getHeight()).intValue());
    //addChartPanel(createSchedulePanel());
    //addTableResizeListeners(myTaskTree, myTreeFacade.getTreeTable().getScrollPane().getViewport());
  }

  private FXToolbarBuilder createScheduleToolbar() {
    return new FXToolbarBuilder().withApplicationFont(TreeTableCellsKt.getApplicationFont())
      .addButton(myCriticalPathAction).addButton(myBaselineAction)
      .withClasses("toolbar-common", "toolbar-small", "toolbar-chart", "align-right");
  }

  private Component createSchedulePanel() {
    return createScheduleToolbar().withScene().build().getComponent();
  }

  JComponent getComponent() {
    if (myComponent == null) {
      myComponent = createContentComponent();
    }
    return myComponent;
  }


  private final Label filterTaskLabel = new Label();

  private Supplier<TaskFilterActionSet> filterActions = Suppliers.memoize(() -> new TaskFilterActionSet(taskTable.getFilterManager()));
  //private final TaskFilterActionSet
  @Override
  protected Component createButtonPanel() {
    return createToolbarBuilder().withScene()
      .build()
      .getComponent();
  }

  private FXToolbarBuilder createToolbarBuilder() {
    Button tableFilterButton = ToolbarKt.createButton(new TableButtonAction("taskTable.tableMenuFilter"), true);
    tableFilterButton.setOnAction(event -> {
      var tableFilterMenu = new ContextMenu();
      tableFilterMenu.getItems().clear();
      filterActions.get().tableFilterActions(new MenuBuilderFx(tableFilterMenu));
      tableFilterMenu.show(tableFilterButton, Side.BOTTOM, 0.0, 0.0);
      event.consume();
    });

    Button tableManageColumnButton = ToolbarKt.createButton(new TableButtonAction("taskTable.tableMenuToggle"), true);
    Objects.requireNonNull(tableManageColumnButton).setOnAction(event -> {
        myTaskActions.getManageColumnsAction().actionPerformed(null);
        event.consume();
    });

    HBox filterComponent = new HBox(0, filterTaskLabel, tableFilterButton, tableManageColumnButton);
    return new FXToolbarBuilder()
        .addButton(myTaskActions.getUnindentAction().asToolbarAction())
        .addButton(myTaskActions.getIndentAction().asToolbarAction())
        .addButton(myTaskActions.getMoveUpAction().asToolbarAction())
        .addButton(myTaskActions.getMoveDownAction().asToolbarAction())
        .addButton(myTaskActions.getLinkTasksAction().asToolbarAction())
        .addButton(myTaskActions.getUnlinkTasksAction().asToolbarAction())
        .addTail(filterComponent)
        .withClasses("toolbar-common", "toolbar-small", "task-filter");
  }

  @NotNull
  @Override
  public Map<String, String> getPersistentAttributes() {
    var result = new HashMap<String, String>();
    result.put("divider", String.valueOf(myViewComponents.getSplitPane().getDividerPositions()[0]));
    return result;
  }

  @Override
  public void setPersistentAttributes(@NotNull Map<String, String> persistentAttributes) {
    String strDivider = persistentAttributes.get("divider");
    if (strDivider != null && myViewComponents!= null) {
      myViewComponents.getSplitPane().setDividerPosition(0, Double.parseDouble(strDivider));
    }
  }

  static class TableButtonAction extends GPAction {
    TableButtonAction(String id) {
      super(id);
      setFontAwesomeLabel(UIUtil.getFontawesomeLabel(this));
    }
    @Override
    public void actionPerformed(ActionEvent e) {
    }
  }

  @Override
  public JComponent getChartComponent() {
    return myGanttChart;
  }

  @Override
  protected @NotNull Component getTreeComponent() {
    var jfxPanel = new JFXPanel();
    this.taskTable = setupTaskTable();
    jfxPanel.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        // Otherwise pressing Delete when editing a task name will delete the task itself.
        if (e.getKeyCode() == KeyEvent.VK_DELETE && taskTable.getTreeTable().getEditingCell() != null) {
          e.consume();
        }
      }
    });
    Platform.runLater(() -> {
      jfxPanel.setScene(new Scene(taskTable.getControl()));
      setHeaderHeight(() -> taskTable.getHeaderHeightProperty().intValue());
      myInitializationCompleted.invoke();
    });
    taskTable.setRequestSwingFocus(() -> {
      jfxPanel.requestFocus();
      return null;
    });
    taskTable.setSwingComponent(jfxPanel);
    taskTable.getColumnListWidthProperty().addListener((observable, oldValue, newValue) ->
      SwingUtilities.invokeLater(() -> setTableWidth(newValue.component1() + newValue.component2()))
    );

    return jfxPanel;
  }

  private TaskTable setupTaskTable() {
    var taskTable = myTaskTableSupplier.get();
    taskTable.getHeaderHeightProperty().addListener((observable, oldValue, newValue) -> updateTimelineHeight());
    taskTable.loadDefaultColumns();
    taskTable.getFilterManager().getHiddenTaskCount().addListener((obs,  oldValue,  newValue) -> Platform.runLater(() -> {
      if (newValue.intValue() != 0) {
        filterTaskLabel.setText(GanttLanguage.getInstance().formatText("taskTable.toolbar.tasksHidden", newValue.intValue()));
      } else {
        filterTaskLabel.setText("");
      }
    }));
    return taskTable;
  }

  // //////////////////////////////////////////////
  // GPView
//  @Override
//  public void setActive(boolean active) {
//    if (active) {
//      //myTaskTree.requestFocus();
//      this.taskTable.initUserKeyboardInput();
//      myTaskActions.getCreateAction().updateAction();
//    }
//  }

  @Override
  public Chart getChart() {
    return myWorkbenchFacade.getGanttChart();
  }

  @Override
  public Component getViewComponent() {
    return getComponent();
  }

  @Override
  public Node getNode() {
    myInitializationCompleted.invoke();
    myViewComponents = ViewPaneKt.createViewComponents(
      /*toolbarBuilder=*/      () -> createToolbarBuilder().build().getToolbar$ganttproject(),
      /*tableBuilder=*/        () -> {
        taskTable = setupTaskTable();
        return taskTable.getTreeTable();
      },
      /*chartToolbarBuilder=*/ () -> {
        var chartToolbarBox = new HBox();
        chartToolbarBox.getChildren().add(createNavigationToolbarBuilder().build().getToolbar$ganttproject());
        chartToolbarBox.getChildren().add(createScheduleToolbar().build().getToolbar$ganttproject());
        return chartToolbarBox;
      },
      /*chartBuilder=*/        this::getChartComponent,
      myWorkbenchFacade.getDpiOption()
    );
    setHeaderHeight(() -> taskTable.getHeaderHeightProperty().intValue());
    return myViewComponents.getSplitPane();
  }

  @Override
  public String getId() {
    return "ganttChart";
  }
}
