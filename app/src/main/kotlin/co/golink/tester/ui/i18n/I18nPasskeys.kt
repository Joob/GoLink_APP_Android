package co.golink.tester.ui.i18n

/** Textos das passkeys (chave = frase PT, como no resto da app). */
object I18nPasskeys {
    // pt → (en, fr, es)
    private val rows: Map<String, Triple<String, String, String>> = mapOf(
        "Entrar com passkey" to Triple("Sign in with a passkey", "Se connecter avec une clé d’accès", "Iniciar sesión con llave de acceso"),
        "Não há passkeys para esta app neste aparelho" to Triple("There are no passkeys for this app on this device", "Aucune clé d’accès pour cette app sur cet appareil", "No hay llaves de acceso para esta app en este dispositivo"),
        "Passkey indisponível neste aparelho" to Triple("Passkeys are unavailable on this device", "Clés d’accès indisponibles sur cet appareil", "Llaves de acceso no disponibles en este dispositivo"),
        "A passkey não foi aceite. Tenta de novo ou usa a password." to Triple("The passkey was not accepted. Try again or use your password.", "La clé d’accès n’a pas été acceptée. Réessayez ou utilisez votre mot de passe.", "La llave de acceso no fue aceptada. Inténtalo de nuevo o usa tu contraseña."),
        "Passkeys" to Triple("Passkeys", "Clés d’accès", "Llaves de acceso"),
        "Entra com a impressão digital, o rosto ou o PIN do aparelho em vez da password e do código de segurança. As passkeys não podem ser roubadas por phishing." to Triple(
            "Sign in with your fingerprint, face or device PIN instead of a password and security code. Passkeys cannot be phished.",
            "Connectez-vous avec votre empreinte, votre visage ou le code de l’appareil au lieu du mot de passe et du code de sécurité. Les clés d’accès résistent à l’hameçonnage.",
            "Inicia sesión con tu huella, tu cara o el PIN del dispositivo en lugar de la contraseña y el código de seguridad. Las llaves de acceso no se pueden robar con phishing.",
        ),
        "Ainda não tens passkeys." to Triple("You have no passkeys yet.", "Vous n’avez encore aucune clé d’accès.", "Todavía no tienes llaves de acceso."),
        "Adicionar passkey" to Triple("Add a passkey", "Ajouter une clé d’accès", "Añadir llave de acceso"),
        "Passkey adicionada." to Triple("Passkey added.", "Clé d’accès ajoutée.", "Llave de acceso añadida."),
        "Por segurança, termina sessão e volta a entrar; depois adiciona a passkey nos 15 minutos seguintes." to Triple("For security, sign out and sign in again, then add the passkey within 15 minutes.", "Par sécurité, déconnectez-vous puis reconnectez-vous, et ajoutez la clé d’accès dans les 15 minutes.", "Por seguridad, cierra sesión y vuelve a entrar; después añade la llave de acceso en los 15 minutos siguientes."),
        "Passkey removida." to Triple("Passkey removed.", "Clé d’accès supprimée.", "Llave de acceso eliminada."),
        "Este aparelho já tem uma passkey para a tua conta." to Triple("This device already has a passkey for your account.", "Cet appareil a déjà une clé d’accès pour votre compte.", "Este dispositivo ya tiene una llave de acceso para tu cuenta."),
        "Password incorreta ou passkey inválida." to Triple("Wrong password or invalid passkey.", "Mot de passe incorrect ou clé d’accès invalide.", "Contraseña incorrecta o llave de acceso no válida."),
        "Não foi possível adicionar a passkey." to Triple("The passkey could not be added.", "Impossible d’ajouter la clé d’accès.", "No se pudo añadir la llave de acceso."),
        "A confirmação com a passkey falhou." to Triple("Confirmation with the passkey failed.", "La confirmation avec la clé d’accès a échoué.", "La confirmación con la llave de acceso falló."),
        "último uso %s" to Triple("last used %s", "dernière utilisation %s", "último uso %s"),
        "nunca usada" to Triple("never used", "jamais utilisée", "nunca usada"),
        "Adicionada %s" to Triple("Added %s", "Ajoutée %s", "Añadida %s"),
        "sincronizada" to Triple("synced", "synchronisée", "sincronizada"),
        "Confirma a tua identidade" to Triple("Confirm it’s you", "Confirmez votre identité", "Confirma tu identidad"),
        "Esta passkey já não existe na tua conta. Remove-a do gestor de passwords e entra de outra forma." to Triple("This passkey no longer exists on your account. Remove it from your password manager and sign in another way.", "Cette clé d’accès n’existe plus sur votre compte. Supprimez-la de votre gestionnaire de mots de passe et connectez-vous autrement.", "Esta llave de acceso ya no existe en tu cuenta. Elimínala de tu gestor de contraseñas y entra de otra forma."),
        "Para remover uma passkey, confirma com a password da conta." to Triple("To remove a passkey, confirm with your account password.", "Pour supprimer une clé d’accès, confirmez avec le mot de passe du compte.", "Para eliminar una llave de acceso, confirma con la contraseña de la cuenta."),
        "Por segurança, termina sessão e volta a entrar; depois remove a passkey nos 15 minutos seguintes." to Triple("For security, sign out and sign in again, then remove the passkey within 15 minutes.", "Par sécurité, déconnectez-vous puis reconnectez-vous, et supprimez la clé d’accès dans les 15 minutes.", "Por seguridad, cierra sesión y vuelve a entrar; después elimina la llave de acceso en los 15 minutos siguientes."),
        "Para adicionar uma passkey, confirma com a password da conta." to Triple("To add a passkey, confirm with your account password.", "Pour ajouter une clé d’accès, confirmez avec le mot de passe du compte.", "Para añadir una llave de acceso, confirma con la contraseña de la cuenta."),
        "Usar passkey em vez da password" to Triple("Use a passkey instead of the password", "Utiliser une clé d’accès au lieu du mot de passe", "Usar una llave de acceso en lugar de la contraseña"),
        "Confirmado com a tua passkey." to Triple("Confirmed with your passkey.", "Confirmé avec votre clé d’accès.", "Confirmado con tu llave de acceso."),
        "Remover passkey?" to Triple("Remove passkey?", "Supprimer la clé d’accès ?", "¿Eliminar la llave de acceso?"),
        "Deixas de poder entrar com \"%s\"." to Triple("You will no longer be able to sign in with \"%s\".", "Vous ne pourrez plus vous connecter avec « %s ».", "Ya no podrás iniciar sesión con «%s»."),
        "Renomear" to Triple("Rename", "Renommer", "Renombrar"),
        "Remover" to Triple("Remove", "Supprimer", "Eliminar"),
        "Nome" to Triple("Name", "Nom", "Nombre"),
        "Continuar" to Triple("Continue", "Continuer", "Continuar"),
        "Password atual" to Triple("Current password", "Mot de passe actuel", "Contraseña actual"),
    )

    val en: Map<String, String> = rows.mapValues { it.value.first }
    val fr: Map<String, String> = rows.mapValues { it.value.second }
    val es: Map<String, String> = rows.mapValues { it.value.third }
}
