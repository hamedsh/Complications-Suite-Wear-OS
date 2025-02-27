/*
 * Copyright 2022 amoledwatchfaces™
 * support@amoledwatchfaces.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.weartools.weekdayutccomp.complication

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon.createWithBitmap
import android.graphics.drawable.Icon.createWithResource
import android.util.Log
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.weartools.weekdayutccomp.R
import com.weartools.weekdayutccomp.R.drawable
import com.weartools.weekdayutccomp.preferences.UserPreferences
import com.weartools.weekdayutccomp.preferences.UserPreferencesRepository
import com.weartools.weekdayutccomp.utils.DrawMoonBitmap
import com.weartools.weekdayutccomp.utils.MoonPhaseHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.shredzone.commons.suncalc.MoonIllumination
import org.shredzone.commons.suncalc.MoonPosition
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject

@AndroidEntryPoint
class MoonPhaseComplicationService : SuspendingComplicationDataSourceService() {

    @Inject
    lateinit var dataStore: DataStore<UserPreferences>
    private val preferences by lazy { UserPreferencesRepository(dataStore).getPreferences() }
    override fun onComplicationActivated(
        complicationInstanceId: Int,
        type: ComplicationType
    ) {
        Log.d(TAG, "onComplicationActivated(): $complicationInstanceId")
        reqPermissionFunction(applicationContext)
    }

    /** CHECK LOCATION PERMISSION + CONSIDER LOCATION TOAST */
    private fun reqPermissionFunction(context: Context) {

        runBlocking {
            val result = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (result == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission granted")
            } else {
                Toast.makeText(context, getString(R.string.enable_permission_toast_consider), Toast.LENGTH_LONG).show()
            }
        }
    }

private fun openScreen(): PendingIntent? {

    val calendarIntent = Intent()
    calendarIntent.action = Intent.ACTION_MAIN
    calendarIntent.addCategory(Intent.CATEGORY_APP_CALENDAR)

    return PendingIntent.getActivity(
        this, 0, calendarIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

override fun getPreviewData(type: ComplicationType): ComplicationData? {
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "100%").build(),
            contentDescription = PlainComplicationText.Builder(text = "100%")
                .build()
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    image = createWithResource(this, drawable.x_moon_full),
                ).build()
            )
            .setTapAction(null)
            .build()

        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = 50f,
            min = 0f,
            max =  100f,
            contentDescription = PlainComplicationText.Builder(text = "Visibility").build()
            )
            .setMonochromaticImage(
                MonochromaticImage.Builder(createWithResource(this, drawable.x_moon_first_quarter)).build()
            )
            .setTapAction(null)
            .build()

        ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
            monochromaticImage = MonochromaticImage.Builder(
                createWithResource(this, drawable.x_moon_full)
            )
                .build(),
            contentDescription = PlainComplicationText.Builder(text = "MONO_IMG.").build()
        )
            .setTapAction(null)
            .build()


        ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = createWithResource(this, drawable.x_moon_full),
                type = SmallImageType.ICON
            ).build(),
            contentDescription = PlainComplicationText.Builder(text = "SMALL_IMAGE.").build()
        )
            .setTapAction(null)
            .build()

        else -> {null}
    }
}


    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {

        val prefs = preferences.first()
        val simpleIcon = prefs.isSimpleIcon
        val isnorthernHemi = prefs.isHemisphere

        val lat = prefs.latitude
        val long = prefs.longitude

        /**
         * CALCULATE MOON
         */
        val set1 = MoonPosition.compute().now().at(lat,long).execute()
        val set2 = MoonIllumination.compute().now().execute()

        val parallacticAngle = set1.parallacticAngle.toFloat()
        val phase = set2.phase.toFloat()
        val angle = set2.angle.toFloat()*(-1)
        val fraction = set2.fraction
        val phaseName = set2.closestPhase.name

    val df = DecimalFormat("#.#")
    df.roundingMode = RoundingMode.HALF_UP
    val visibilityok = df.format(fraction*100).toString()

    return when (request.complicationType) {

        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "$visibilityok%").build(),
            contentDescription = PlainComplicationText.Builder(text = getString(R.string.moon_comp_name)).build())

            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    if (simpleIcon) {
                        createWithResource(this,
                            MoonPhaseHelper.getSimpleIcon(phaseName = phaseName,isnorthernHemi))
                    }
                    else {
                        createWithBitmap(
                            DrawMoonBitmap.getLunarPhaseBitmap(
                                fraction = fraction,
                                angle = angle,
                                parallacticAngle = parallacticAngle,
                                lat = lat,
                                hemi = isnorthernHemi))
                    }
                ).build()
            )
            .setTapAction(openScreen())
            .build()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "$visibilityok %").build(),
            contentDescription = PlainComplicationText.Builder(text = getString(R.string.moon_comp_name)).build())

            .setTitle(PlainComplicationText.Builder(text = MoonPhaseHelper.getMoonPhaseName(phaseName = phaseName, context = this)).build())
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    if (simpleIcon) {
                        createWithResource(this,
                            MoonPhaseHelper.getSimpleIcon(phaseName = phaseName,isnorthernHemi))
                    }
                    else {
                        createWithBitmap(
                            DrawMoonBitmap.getLunarPhaseBitmap(
                            fraction = fraction,
                            angle = angle,
                            parallacticAngle = parallacticAngle,
                            lat = lat,
                            hemi = isnorthernHemi))
                    }
                ).build()
            )
            .setTapAction(openScreen())
            .build()

        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = phase,
            min = -180.0f,
            max =  180.0f,
            contentDescription = PlainComplicationText
                .Builder(text = "Visibility").build()
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    if (simpleIcon) {
                        createWithResource(this,
                            MoonPhaseHelper.getSimpleIcon(phaseName = phaseName,isnorthernHemi))
                    }
                    else {createWithBitmap(
                        DrawMoonBitmap.getLunarPhaseBitmap(
                        fraction = fraction,
                        angle = angle,
                        parallacticAngle = parallacticAngle,
                        lat = lat,
                        hemi = isnorthernHemi))
                    }
                ).build()
            )
            .setTapAction(openScreen())
            .build()

        ComplicationType.MONOCHROMATIC_IMAGE -> {

            MonochromaticImageComplicationData.Builder(
                monochromaticImage = MonochromaticImage.Builder(
                    if (simpleIcon) {
                        createWithResource(this,
                            MoonPhaseHelper.getSimpleIcon(phaseName = phaseName,isnorthernHemi))
                    }
                    else {createWithBitmap(
                        DrawMoonBitmap.getLunarPhaseBitmap(
                        fraction = fraction,
                        angle = angle,
                        parallacticAngle = parallacticAngle,
                        lat = lat,
                        hemi = isnorthernHemi))
                    }).build(),
                contentDescription = PlainComplicationText.Builder(text = "MONO_IMG.").build()
            )
                .setTapAction(openScreen())

                .build()
        }


        ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
            smallImage = SmallImage.Builder(
                image = if (simpleIcon) {
                    createWithResource(this,
                        MoonPhaseHelper.getSimpleIcon(phaseName = phaseName,isnorthernHemi))
                }
                else {createWithBitmap(
                    DrawMoonBitmap.getLunarPhaseBitmap(
                    fraction = fraction,
                    angle = angle,
                    parallacticAngle = parallacticAngle,
                    lat = lat,
                    hemi = isnorthernHemi))
                     },
                type = SmallImageType.ICON
            ).build(),
            contentDescription = PlainComplicationText.Builder(text = "SMALL_IMAGE.").build()
        )
            .setTapAction(openScreen())
            .build()

        else -> {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Unexpected complication type ${request.complicationType}")
            }
            null
        }

    }
}
}

