package com.kleanthi.obsidianwidget.config

/**
 * Same screen as [WidgetConfigActivity], but declared with an empty
 * taskAffinity so the widget ⚙ button and the app's widget list can open
 * settings in their own throwaway task without resurfacing the app task.
 *
 * The base activity (used by the launcher's APPWIDGET_CONFIGURE flow) must
 * keep the default affinity: if it runs in its own task, setResult() never
 * reaches the launcher's startActivityForResult, which then treats the
 * configuration as cancelled and removes the widget ("Couldn't add widget").
 */
class WidgetReconfigActivity : WidgetConfigActivity()
