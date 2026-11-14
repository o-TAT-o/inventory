# Compose・AndroidX・SQLDelight は、それぞれの配布物に同梱された規則で守られる。
# 列挙型は名前（文字列）で DB に保存しているので、名前を変えないために keep する。
-keepclassmembers enum io.github.otato.traininglog.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
