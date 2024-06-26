/*
Copyright 2022 BarD Software s.r.o

This file is part of GanttProject, an open-source project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.sourceforge.ganttproject.parser

import biz.ganttproject.core.io.XmlTasks
import biz.ganttproject.lib.fx.SimpleTreeCollapseView
import net.sourceforge.ganttproject.TestSetupHelper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TestTaskLoader {
  @Test
  fun `empty UID is ignored, non-empty is preserved`() {
      val xmlTask1 = XmlTasks.XmlTask(id = 1, uid = "", name = "Task1", startDate = "2022-04-19")
      val xmlTask2 = XmlTasks.XmlTask(id = 2, uid = "c4f3b4b3", name = "Task2", startDate = "2022-04-19")

      val taskManager = TestSetupHelper.newTaskManagerBuilder().build()
      val taskLoader = TaskLoader(taskManager, SimpleTreeCollapseView())
      val task1 = taskLoader.loadTask(null, xmlTask1)
      val task2 = taskLoader.loadTask(null, xmlTask2)
      assert(task1.uid.isNotBlank())
      assertEquals("c4f3b4b3", task2.uid)
  }

  @Test
  fun `calculated and explicit task cost`() {
      val xmlTask1 = XmlTasks.XmlTask(id = 1, uid = "", name = "Task1", startDate = "2022-04-19")
      val xmlTask2 = XmlTasks.XmlTask(id = 2, uid = "c4f3b4b3", name = "Task2", startDate = "2022-04-19").also {
          it.costManualValue = 129.toBigDecimal()
          it.isCostCalculated = false
      }

      val taskManager = TestSetupHelper.newTaskManagerBuilder().build()
      val taskLoader = TaskLoader(taskManager, SimpleTreeCollapseView())
      taskLoader.loadTask(null, xmlTask1).let {
          assertTrue(it.cost.isCalculated)
      }
      taskLoader.loadTask(null, xmlTask2).let {
          assertFalse(it.cost.isCalculated)
          assertEquals(129.toBigDecimal(), it.cost.manualValue)
      }
  }

  @Test
  fun `custom property values`() {
      val xmlTask1 = XmlTasks.XmlTask(id = 1, uid = "", name = "Task1", startDate = "2022-04-19",
          customPropertyValues = listOf(XmlTasks.XmlTask.XmlCustomProperty("tpc0", "true"), XmlTasks.XmlTask.XmlCustomProperty("tpc1", "foobar"))
      )
      val taskManager = TestSetupHelper.newTaskManagerBuilder().build()
      val tpc0 = taskManager.customPropertyManager.createDefinition( "tpc0", "boolean", "b", "false")
      val tpc1 = taskManager.customPropertyManager.createDefinition( "tpc1", "text", "s", "")
      val taskLoader = TaskLoader(taskManager, SimpleTreeCollapseView())
      taskLoader.loadTask(null, xmlTask1).let {
          assertEquals(true, it.customValues.getValue(tpc0))
          assertEquals("foobar", it.customValues.getValue(tpc1))
      }

  }
}