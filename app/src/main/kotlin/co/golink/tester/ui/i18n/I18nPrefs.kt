package co.golink.tester.ui.i18n

/** Preferências da conta e 2FA nativo (chave = frase PT, como no resto da app). */
object I18nPrefs {
    // pt → (en, fr, es)
    private val rows: Map<String, Triple<String, String, String>> = mapOf(
        // Menu / Preferências
        "Preferências" to Triple("Preferences", "Préférences", "Preferencias"),
        "Tema, idioma, datas e emails" to Triple("Theme, language, dates and emails", "Thème, langue, dates et e-mails", "Tema, idioma, fechas y correos"),
        "Aparência" to Triple("Appearance", "Apparence", "Apariencia"),
        "Idioma" to Triple("Language", "Langue", "Idioma"),
        "Formato de data" to Triple("Date format", "Format de date", "Formato de fecha"),
        "Automático (pelo idioma)" to Triple("Automatic (by language)", "Automatique (selon la langue)", "Automático (según el idioma)"),
        "Início da semana" to Triple("Week starts on", "Début de la semaine", "Inicio de la semana"),
        "Emails" to Triple("Emails", "E-mails", "Correos"),
        "Novidades" to Triple("Product news", "Nouveautés", "Novedades"),
        "Novas funcionalidades e anúncios." to Triple("New features and announcements.", "Nouvelles fonctionnalités et annonces.", "Nuevas funciones y anuncios."),
        "Dicas" to Triple("Tips", "Astuces", "Consejos"),
        "Sugestões para tirares mais partido da conta." to Triple("Suggestions to get more out of your account.", "Des conseils pour mieux profiter de votre compte.", "Sugerencias para sacar más partido a tu cuenta."),
        "Resumo semanal" to Triple("Weekly summary", "Résumé hebdomadaire", "Resumen semanal"),
        "À segunda-feira: o que enviaste e partilhaste." to Triple("Every Monday: what you uploaded and shared.", "Chaque lundi : ce que vous avez envoyé et partagé.", "Cada lunes: lo que subiste y compartiste."),
        "Segurança e conta" to Triple("Security and account", "Sécurité et compte", "Seguridad y cuenta"),
        "Avisos importantes. Não podem ser desligados." to Triple("Important notices. These can't be turned off.", "Avis importants. Ils ne peuvent pas être désactivés.", "Avisos importantes. No se pueden desactivar."),
        // Bloqueio da app
        "Sempre ligado com «Agora»: a app bloqueia ao sair." to Triple("Always on with “Now”: the app locks when you leave.", "Toujours activé avec « Maintenant » : l’app se verrouille quand vous la quittez.", "Siempre activado con «Ahora»: la app se bloquea al salir."),
        // 2FA
        "Pede um código da app autenticadora além da password." to Triple("Asks for a code from your authenticator app as well as your password.", "Demande un code de votre app d’authentification en plus du mot de passe.", "Pide un código de tu app de autenticación además de la contraseña."),
        "Ver códigos de recuperação" to Triple("Show recovery codes", "Afficher les codes de récupération", "Ver códigos de recuperación"),
        "Gerar novos códigos" to Triple("Generate new codes", "Générer de nouveaux codes", "Generar códigos nuevos"),
        "Desativar" to Triple("Turn off", "Désactiver", "Desactivar"),
        "Ativar" to Triple("Turn on", "Activer", "Activar"),
        "1. Lê o código QR com a app autenticadora (ou copia a chave)." to Triple("1. Scan the QR code with your authenticator app (or copy the key).", "1. Scannez le code QR avec votre app d’authentification (ou copiez la clé).", "1. Escanea el código QR con tu app de autenticación (o copia la clave)."),
        "Copiar" to Triple("Copy", "Copier", "Copiar"),
        "Abrir na app autenticadora" to Triple("Open in authenticator app", "Ouvrir dans l’app d’authentification", "Abrir en la app de autenticación"),
        "Nenhuma app autenticadora instalada" to Triple("No authenticator app installed", "Aucune app d’authentification installée", "No hay ninguna app de autenticación instalada"),
        "2. Escreve o código de 6 dígitos que a app mostra." to Triple("2. Enter the 6-digit code shown in the app.", "2. Saisissez le code à 6 chiffres affiché par l’app.", "2. Escribe el código de 6 dígitos que muestra la app."),
        "Código" to Triple("Code", "Code", "Código"),
        "Confirmar" to Triple("Confirm", "Confirmer", "Confirmar"),
        "Verificação em dois passos ativada" to Triple("Two-step verification turned on", "Validation en deux étapes activée", "Verificación en dos pasos activada"),
        "Verificação em dois passos ativada. " to Triple("Two-step verification turned on. ", "Validation en deux étapes activée. ", "Verificación en dos pasos activada. "),
        "Verificação em dois passos desativada" to Triple("Two-step verification turned off", "Validation en deux étapes désactivée", "Verificación en dos pasos desactivada"),
        "Guarda estes códigos num sítio seguro: cada um serve uma vez se perderes o telemóvel." to Triple(
            "Keep these codes somewhere safe: each one works once if you lose your phone.",
            "Conservez ces codes en lieu sûr : chacun ne sert qu’une fois si vous perdez votre téléphone.",
            "Guarda estos códigos en un lugar seguro: cada uno sirve una vez si pierdes el móvil.",
        ),
        "Copiar códigos" to Triple("Copy codes", "Copier les codes", "Copiar códigos"),
        "Ativar verificação em dois passos" to Triple("Turn on two-step verification", "Activer la validation en deux étapes", "Activar la verificación en dos pasos"),
        "Desativar verificação em dois passos?" to Triple("Turn off two-step verification?", "Désactiver la validation en deux étapes ?", "¿Desactivar la verificación en dos pasos?"),
        "Códigos de recuperação" to Triple("Recovery codes", "Codes de récupération", "Códigos de recuperación"),
        "Gerar novos códigos?" to Triple("Generate new codes?", "Générer de nouveaux codes ?", "¿Generar códigos nuevos?"),
        "A conta fica protegida só pela password." to Triple("Your account will be protected by your password only.", "Votre compte ne sera protégé que par le mot de passe.", "Tu cuenta quedará protegida solo por la contraseña."),
        "Os códigos atuais deixam de funcionar." to Triple("Your current codes will stop working.", "Les codes actuels ne fonctionneront plus.", "Los códigos actuales dejarán de funcionar."),
        "Confirma que és tu." to Triple("Confirm it's you.", "Confirmez que c’est bien vous.", "Confirma que eres tú."),
        // Faturação / erros
        "Email inválido" to Triple("Invalid email", "E-mail invalide", "Correo no válido"),
        "Cartão" to Triple("Card", "Carte", "Tarjeta"),
        "Ocorreu um erro. Tenta novamente." to Triple("Something went wrong. Please try again.", "Une erreur s’est produite. Réessayez.", "Se ha producido un error. Inténtalo de nuevo."),
        "Sem ligação ao servidor. Tenta novamente." to Triple("Can't reach the server. Please try again.", "Impossible de joindre le serveur. Réessayez.", "No se puede conectar con el servidor. Inténtalo de nuevo."),
    )

    val en: Map<String, String> = rows.mapValues { it.value.first }
    val fr: Map<String, String> = rows.mapValues { it.value.second }
    val es: Map<String, String> = rows.mapValues { it.value.third }
}
