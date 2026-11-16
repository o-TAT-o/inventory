# Compose・AndroidX・SQLDelight は、それぞれの配布物に同梱された規則で守られる。
# 列挙型は名前（文字列）で DB に保存しているので、名前を変えないために keep する。
-keepclassmembers enum io.github.otato.traininglog.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 広告の SDK が使う WorkManager は、データベース（Room）の実装クラスを、名前から探して作る。
# 消されると、起動した直後に落ちる（「Failed to create an instance of androidx.work.impl.WorkDatabase」）ので、残す。
-keep class * extends androidx.room.RoomDatabase { <init>(); }
