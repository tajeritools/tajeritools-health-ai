package ir.tajeritools.healthai

data class TraditionalQuestion(
    val text: String,
    val tags: List<String>
)

object TraditionalQuestionnaire {
    val questions = listOf(
        TraditionalQuestion("اغلب احساس گرمای زیاد یا تشنگی شدید داری؟", listOf("TCM_HEAT", "PITTA")),
        TraditionalQuestion("اغلب احساس سرما، دست‌وپای سرد یا علاقه به نوشیدنی گرم داری؟", listOf("TCM_COLD", "VATA")),
        TraditionalQuestion("بعد از غذا احساس سنگینی، نفخ یا خواب‌آلودگی داری؟", listOf("TCM_DAMP", "KAPHA")),
        TraditionalQuestion("خستگی مداوم یا کمبود انرژی روزانه داری؟", listOf("TCM_QI_DEF", "VATA")),
        TraditionalQuestion("زود عصبانی می‌شوی یا گرگرفتگی و تحریک‌پذیری داری؟", listOf("TCM_HEAT", "PITTA")),
        TraditionalQuestion("خشکی پوست، یبوست یا بی‌قراری بیشتر داری؟", listOf("VATA")),
        TraditionalQuestion("تعریق زیاد یا احساس سوزش معده داری؟", listOf("PITTA", "TCM_HEAT")),
        TraditionalQuestion("افزایش وزن آسان، کندی و خواب سنگین داری؟", listOf("KAPHA", "TCM_DAMP")),
        TraditionalQuestion("خواب سبک و منقطع یا فکرهای سریع داری؟", listOf("VATA")),
        TraditionalQuestion("تورم، احتباس مایع یا احساس سنگینی بدن داری؟", listOf("TCM_DAMP", "KAPHA")),
        TraditionalQuestion("رنگ‌پریدگی همراه با ضعف یا سرگیجه مکرر داری؟", listOf("TCM_QI_DEF")),
        TraditionalQuestion("دردهای ثابت و موضعی یا احساس گرفتگی طولانی‌مدت داری؟", listOf("TCM_BLOOD_STASIS")),
        TraditionalQuestion("نبض یا ضربان را گاهی نامنظم و همراه اضطراب حس می‌کنی؟", listOf("VATA")),
        TraditionalQuestion("اشتها زیاد ولی تحمل گرسنگی کم داری؟", listOf("PITTA")),
        TraditionalQuestion("اشتها کم و هضم متغیر داری؟", listOf("VATA", "TCM_QI_DEF")),
        TraditionalQuestion("مخاط، خلط یا احساس گرفتگی و رطوبت زیاد داری؟", listOf("KAPHA", "TCM_DAMP")),
        TraditionalQuestion("صورت یا چشم‌ها گاهی قرمز و داغ می‌شوند؟", listOf("TCM_HEAT", "PITTA")),
        TraditionalQuestion("بعد از استراحت هم انرژی پایین می‌ماند؟", listOf("TCM_QI_DEF", "KAPHA"))
    )

    fun summarize(answers: List<Boolean>): String {
        val scores = linkedMapOf(
            "TCM_HEAT" to 0, "TCM_COLD" to 0, "TCM_DAMP" to 0,
            "TCM_QI_DEF" to 0, "TCM_BLOOD_STASIS" to 0,
            "VATA" to 0, "PITTA" to 0, "KAPHA" to 0
        )
        questions.forEachIndexed { i, q ->
            if (answers.getOrNull(i) == true) q.tags.forEach { tag ->
                scores[tag] = (scores[tag] ?: 0) + 1
            }
        }
        val ordered = scores.entries.sortedByDescending { it.value }
        val tcm = ordered.filter { it.key.startsWith("TCM_") }.take(3)
        val ayu = ordered.filter { !it.key.startsWith("TCM_") }.take(3)

        fun label(k: String) = when (k) {
            "TCM_HEAT" -> "Heat / گرمی"
            "TCM_COLD" -> "Cold / سردی"
            "TCM_DAMP" -> "Dampness / رطوبت"
            "TCM_QI_DEF" -> "Qi deficiency / کمبود چی"
            "TCM_BLOOD_STASIS" -> "Blood stasis / رکود خون"
            "VATA" -> "Vata"
            "PITTA" -> "Pitta"
            "KAPHA" -> "Kapha"
            else -> k
        }

        return buildString {
            appendLine("نتیجه محلی پرسشنامه:")
            appendLine("الگوهای TCM:")
            tcm.forEach { appendLine("• ${label(it.key)}: امتیاز ${it.value}") }
            appendLine("الگوهای Ayurveda:")
            ayu.forEach { appendLine("• ${label(it.key)}: امتیاز ${it.value}") }
            appendLine()
            append("این امتیازها فقط دسته‌بندی سنتی پرسشنامه هستند و تشخیص پزشکی نیستند.")
        }
    }

    fun answersAsText(answers: List<Boolean>): String =
        questions.mapIndexed { i, q ->
            "${i + 1}. ${q.text} پاسخ: ${if (answers.getOrNull(i) == true) "بله" else "خیر"}"
        }.joinToString("\n")
}
