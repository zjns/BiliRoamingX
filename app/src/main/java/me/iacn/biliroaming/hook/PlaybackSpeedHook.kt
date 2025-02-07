package me.iacn.biliroaming.hook

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.from
import me.iacn.biliroaming.orNull
import me.iacn.biliroaming.utils.*
import java.lang.reflect.Field

class PlaybackSpeedHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val newSpeedArray = sPrefs.getString("playback_speed_override", null).let { v ->
        if (v.isNullOrEmpty()) floatArrayOf()
        else v.split(' ').map { it.toFloat() }.toFloatArray()
    }
    private val newSpeedReversedArray = newSpeedArray.reversedArray()
    private val defaultSpeed = sPrefs.getFloat("default_playback_speed", 0F)
    private val longPressSpeed = sPrefs.getFloat("long_press_playback_speed", 0F)

    private var playbackSpeed = defaultSpeed
    private var speedTextGroupField: Field? = null

    @SuppressLint("SetTextI18n")
    override fun startHook() {
        if (defaultSpeed != 0F) {
            instance.hookInfo.playbackSpeed.playerSettingService.run {
                class_.from(mClassLoader)?.hookBeforeMethod(
                    getFloat.orNull, String::class.java, Float::class.javaPrimitiveType
                ) { param ->
                    val key = param.args[0] as String
                    if (key == "player_key_video_speed")
                        param.args[1] = defaultSpeed
                }
            }
        }
        if (longPressSpeed != 0F) {
            instance.hookInfo.playbackSpeed.tripleSpeedServiceList.forEach {
                it.class_.from(mClassLoader)?.hookBeforeMethod(
                    it.updateSpeed.orNull, Float::class.javaPrimitiveType
                ) { param ->
                    val speed = param.args[0]
                    if (speed == 2.0F || speed == 3.0F)
                        param.args[0] = longPressSpeed
                }
            }
        }
        if (newSpeedArray.isEmpty()) return

        instance.hookInfo.playbackSpeed.speedAdapterList.forEach {
            it.class_.from(mClassLoader)?.hookAfterAllConstructors { param ->
                param.thisObject.setObjectField(it.speedArray.orNull, newSpeedArray)
            }
        }
        instance.hookInfo.playbackSpeed.storySuperMenu.run {
            class_.from(mClassLoader)?.hookAfterAllConstructors { param ->
                param.thisObject.setObjectField(speedArray.orNull, newSpeedArray)
            }
        }
        instance.hookInfo.playbackSpeed.menuFuncSegment.run {
            class_.from(mClassLoader)?.hookAfterAllConstructors { param ->
                param.thisObject.setObjectField(speedArray.orNull, newSpeedArray)
            }
        }
        instance.hookInfo.playbackSpeed.newShareService.run {
            class_.from(mClassLoader)?.hookAfterAllConstructors { param ->
                param.thisObject.setObjectField(speedArray.orNull, newSpeedArray)
            }
        }
        instance.hookInfo.playbackSpeed.playSpeedSettingList.forEach { c ->
            val clazz = c.class_.from(mClassLoader)
            val playSpeedTextGroupId = getId("playback_speed_text_group")
            val speedTextColorId = getResId("selector_bplayer_selector_panel_text_pink", "color")
            clazz?.hookAfterAllConstructors { param ->
                val self = param.thisObject
                val speedTextGroupField = this.speedTextGroupField ?: clazz.declaredFields.filter {
                    it.type == ViewGroup::class.java
                }.firstNotNullOfOrNull {
                    it.isAccessible = true
                    if ((it.get(self) as? ViewGroup)?.id == playSpeedTextGroupId) {
                        it
                    } else null
                }?.also { this.speedTextGroupField = it } ?: return@hookAfterAllConstructors
                val speedTextGroup = speedTextGroupField.get(self) as ViewGroup
                val context = speedTextGroup.context
                val scrollView = HorizontalScrollView(context).apply {
                    id = speedTextGroup.id
                    layoutParams = speedTextGroup.layoutParams
                    isHorizontalScrollBarEnabled = false
                }
                val newSpeedTextGroup = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                }.also { scrollView.addView(it) }
                (speedTextGroup.parent as ViewGroup).apply {
                    removeView(speedTextGroup)
                    addView(scrollView)
                }
                val newSpeedText = { speed: Float, id: Int ->
                    TextView(context).apply {
                        text = speed.toString()
                        this.id = id
                        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14F)
                        setTextColor(context.getColor(speedTextColorId))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                }
                val newSpeedIdMap = newSpeedReversedArray.associateWith { View.generateViewId() }
                (newSpeedIdMap).forEach { (speed, id) ->
                    newSpeedText(speed, id).let { newSpeedTextGroup.addView(it) }
                }
                newSpeedTextGroup.children.forEachIndexed { index, view ->
                    when (index) {
                        0 -> view.setPadding(12.dp, 4.dp, 16.dp, 4.dp)

                        newSpeedTextGroup.childCount - 1 ->
                            view.setPadding(16.dp, 4.dp, 12.dp, 4.dp)

                        else -> view.setPadding(16.dp, 4.dp, 16.dp, 4.dp)
                    }
                }
                self.setObjectField(c.speedTextIdArray.orNull, newSpeedIdMap.values.toIntArray())
                speedTextGroupField.set(self, newSpeedTextGroup)
                self.setObjectField(c.speedArray.orNull, newSpeedReversedArray)
            }
        }
        instance.hookInfo.playbackSpeed.podcastSpeedSeekBar.run {
            val pairClass = "kotlin.Pair".from(mClassLoader) ?: return@run
            class_.from(mClassLoader)?.hookAfterAllConstructors { param ->
                val self = param.thisObject
                self.setObjectField(speedArray.orNull, newSpeedReversedArray)
                val speedNameList = self.getObjectFieldAs<MutableList<Any>>(speedNameList.orNull)
                speedNameList.clear()
                newSpeedReversedArray.map { pairClass.new(it, "${it}x") }.let {
                    speedNameList.addAll(it)
                }
                val max = newSpeedReversedArray.lastIndex.coerceAtLeast(0) * 100
                self.callMethodOrNull("setMax", max)
            }
        }
        instance.hookInfo.playbackSpeed.musicPlayerPanel.run {
            class_.from(mClassLoader)?.hookAfterAllConstructors { param ->
                param.thisObject.setObjectField(speedArray.orNull, newSpeedReversedArray)
            }
        }
        instance.playerCoreServiceV2Class?.hookBeforeMethod(
            instance.updateSpeed(), Float::class.javaPrimitiveType
        ) { playbackSpeed = it.args[0] as Float }
        val speedTextId = getResId("player_controller_edit_speed_text", "string")
        instance.hookInfo.playbackSpeed.playerSpeedWidgetList.forEach { w ->
            w.class_.from(mClassLoader)?.replaceMethod(w.update.orNull) { param ->
                val textView = param.thisObject as TextView
                if (playbackSpeed == 1.0F) {
                    textView.setText(speedTextId)
                } else {
                    textView.text = "${playbackSpeed}X"
                }
                null
            }
        }
    }
}
