package co.golink.tester.ui.i18n

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * i18n em runtime: os textos da app estão escritos em PT no código, e este
 * módulo traduz por dicionário (chave = a própria frase PT). `lang` é snapshot
 * state do Compose — mudar o idioma recompõe automaticamente tudo o que lê
 * `tr()`. Frases fora do dicionário caem para o PT original, por isso adicionar
 * traduções é incremental e nunca parte a UI.
 */
object I18n {
    const val PT = "pt"
    const val EN = "en"
    const val FR = "fr"
    const val ES = "es"

    data class Lang(val code: String, val label: String, val flag: String)

    val languages = listOf(
        Lang(PT, "Português", "🇵🇹"),
        Lang(EN, "English", "🇬🇧"),
        Lang(FR, "Français", "🇫🇷"),
        Lang(ES, "Español", "🇪🇸"),
    )

    // Compat: pares (code, label) usados no seletor das Definições.
    val supported = languages.map { it.code to it.label }

    fun current(): Lang = languages.firstOrNull { it.code == lang } ?: languages.first()

    var lang by mutableStateOf(PT)
        private set

    fun init(context: Context) {
        lang = prefs(context).getString(KEY, PT) ?: PT
    }

    fun set(context: Context, code: String) {
        if (code !in listOf(PT, EN, FR, ES)) return
        prefs(context).edit().putString(KEY, code).apply()
        lang = code
    }

    fun t(pt: String): String = when (lang) {
        PT -> pt
        EN -> en[pt] ?: I18nDict.en[pt] ?: pt
        FR -> fr[pt] ?: I18nDict.fr[pt] ?: pt
        ES -> es[pt] ?: I18nDict.es[pt] ?: pt
        else -> pt
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("app_language", Context.MODE_PRIVATE)

    private const val KEY = "lang"

    private val en = mapOf(
        // Comuns / acções
        "Abrir" to "Open", "Adicionar" to "Add", "Descarregar" to "Download",
        "Detalhes" to "Details", "Eliminar" to "Delete",
        "Eliminar permanentemente" to "Delete permanently",
        "Eliminar permanentemente?" to "Delete permanently?",
        "Mover" to "Move", "Mover para o lixo" to "Move to trash",
        "Mover para o lixo?" to "Move to trash?",
        "Partilhar" to "Share", "Editar partilha" to "Edit sharing",
        "Editar item" to "Edit item", "Renomear" to "Rename",
        "Restaurar" to "Restore", "Guardar" to "Save", "Cancelar" to "Cancel",
        "Criar" to "Create", "Fechar" to "Close", "Feito" to "Done",
        "Seguinte" to "Next", "Limpar" to "Clear", "Todas" to "All",
        "Voltar" to "Back", "Sair" to "Log out", "Iniciar" to "Start",
        "Activar" to "Enable", "Permitir" to "Allow", "Tentar de novo" to "Try again",
        "Novo nome" to "New name", "Mais opções" to "More options",
        "Partilhado" to "Shared", "Sem conteúdo" to "No content",
        "Adicionar aos favoritos" to "Add to favourites",
        "Remover dos favoritos" to "Remove from favourites",
        "Converter em pasta de equipa" to "Convert to team folder",
        "Pedido de ficheiros" to "File request",
        // Navegação / browse
        "Os meus ficheiros" to "My files", "Carregamentos recentes" to "Recent uploads",
        "Partilhado comigo" to "Shared with me", "Favoritos" to "Favourites",
        "Lixo" to "Trash", "Lixeira" to "Trash", "Pastas de equipa" to "Team folders",
        "Esta pasta está vazia" to "This folder is empty",
        "Não foi possível carregar" to "Could not load",
        "Criar pasta" to "Create folder", "Criar pasta de equipa" to "Create team folder",
        "Criar pedido de ficheiros" to "Create file request",
        "Carregar ficheiros" to "Upload files", "Carregar pasta" to "Upload folder",
        "Carregamento remoto" to "Remote upload", "Esvaziar lixo" to "Empty trash",
        "Esvaziar lixo?" to "Empty trash?", "Espaço quase esgotado" to "Almost out of space",
        "Armazenamento" to "Storage", "Menu" to "Menu", "Definições" to "Settings",
        "Grelha" to "Grid", "Lista" to "List", "Itens" to "Items", "Dono" to "Owner",
        "Editor" to "Editor", "Apenas ver" to "View only", "Criado em" to "Created at",
        "Atualizado em" to "Updated at", "Expira em" to "Expires in",
        "Desmarcar todos" to "Deselect all", "Limpar selecção" to "Clear selection",
        "Selecionar tudo" to "Select all", "Mais usados" to "Most used",
        "Arrasta aqui as tuas pastas favoritas" to "Drag your favourite folders here",
        "Insere os URLs separados por vírgula ou nova linha" to "Enter URLs separated by comma or new line",
        // Definições
        "Perfil" to "Profile", "Nome e detalhes da conta" to "Name and account details",
        "Alterar a tua password" to "Change your password",
        "Espaço utilizado e disponível" to "Used and available space",
        "Sessões" to "Sessions", "Dispositivos e sessões activas" to "Devices and active sessions",
        "Faturação" to "Billing", "Histórico de transações" to "Transaction history",
        "Segurança da app" to "App security", "Biométrico e PIN" to "Biometrics and PIN",
        "Administração" to "Administration", "Painel" to "Dashboard",
        "Visão geral e estatísticas" to "Overview and statistics",
        "Análises" to "Analytics", "Visitas e visitantes" to "Visits and visitors",
        "Utilizadores" to "Users", "Lista de utilizadores" to "User list",
        "Registos de Convite" to "Invite registrations",
        "Convidar e gerir registos" to "Invite and manage registrations",
        "Notícias" to "News", "Notícia importante na zona de files" to "Important news in the files area",
        "Terminar sessão" to "Log out", "Sair da Conta?" to "Log out of your account?",
        "Tens a certeza que queres sair da tua conta?" to "Are you sure you want to log out?",
        "Idioma" to "Language",
        "Mostrar log" to "Show log", "Limpar cache local" to "Clear local cache",
        "Registo de atividade" to "Activity log", "Sem entradas" to "No entries",
        "Nada registado ainda" to "Nothing logged yet",
        // Backups
        "Backups Automáticos" to "Automatic Backups", "Fazer backup agora" to "Back up now",
        "A enviar…" to "Uploading…", "Itens copiados" to "Backup items",
        "Conteúdos a incluir" to "Content to include",
        "Imagens" to "Images", "Vídeos" to "Videos", "Áudios" to "Audio",
        "Documentos" to "Documents", "Downloads" to "Downloads", "Fotos" to "Photos",
        "Ficheiros" to "Files", "Pastas disponíveis" to "Available folders",
        "Dados móveis" to "Mobile data", "Apenas a carregar" to "Only while charging",
        "Voltar a verificar a galeria" to "Re-scan the gallery",
        "Re-verificar a galeria" to "Re-scan the gallery",
        "A ler pastas…" to "Reading folders…",
        "Sem pastas para esta categoria." to "No folders for this category.",
        "Nenhuma pasta escolhida — toca para selecionar" to "No folder selected — tap to choose",
        "Backup desactivado." to "Backup disabled.",
        "A eliminar…" to "Deleting…", "A mover…" to "Moving…",
        "Ativa uma categoria e escolhe as pastas do telemóvel que entram no backup." to
            "Enable a category and choose which phone folders are backed up.",
    )

    private val fr = mapOf(
        "Abrir" to "Ouvrir", "Adicionar" to "Ajouter", "Descarregar" to "Télécharger",
        "Detalhes" to "Détails", "Eliminar" to "Supprimer",
        "Eliminar permanentemente" to "Supprimer définitivement",
        "Eliminar permanentemente?" to "Supprimer définitivement ?",
        "Mover" to "Déplacer", "Mover para o lixo" to "Mettre à la corbeille",
        "Mover para o lixo?" to "Mettre à la corbeille ?",
        "Partilhar" to "Partager", "Editar partilha" to "Modifier le partage",
        "Editar item" to "Modifier l'élément", "Renomear" to "Renommer",
        "Restaurar" to "Restaurer", "Guardar" to "Enregistrer", "Cancelar" to "Annuler",
        "Criar" to "Créer", "Fechar" to "Fermer", "Feito" to "Terminé",
        "Seguinte" to "Suivant", "Limpar" to "Effacer", "Todas" to "Toutes",
        "Voltar" to "Retour", "Sair" to "Déconnexion", "Iniciar" to "Démarrer",
        "Activar" to "Activer", "Permitir" to "Autoriser", "Tentar de novo" to "Réessayer",
        "Novo nome" to "Nouveau nom", "Mais opções" to "Plus d'options",
        "Partilhado" to "Partagé", "Sem conteúdo" to "Aucun contenu",
        "Adicionar aos favoritos" to "Ajouter aux favoris",
        "Remover dos favoritos" to "Retirer des favoris",
        "Converter em pasta de equipa" to "Convertir en dossier d'équipe",
        "Pedido de ficheiros" to "Demande de fichiers",
        "Os meus ficheiros" to "Mes fichiers", "Carregamentos recentes" to "Envois récents",
        "Partilhado comigo" to "Partagé avec moi", "Favoritos" to "Favoris",
        "Lixo" to "Corbeille", "Lixeira" to "Corbeille", "Pastas de equipa" to "Dossiers d'équipe",
        "Esta pasta está vazia" to "Ce dossier est vide",
        "Não foi possível carregar" to "Chargement impossible",
        "Criar pasta" to "Créer un dossier", "Criar pasta de equipa" to "Créer un dossier d'équipe",
        "Criar pedido de ficheiros" to "Créer une demande de fichiers",
        "Carregar ficheiros" to "Envoyer des fichiers", "Carregar pasta" to "Envoyer un dossier",
        "Carregamento remoto" to "Envoi distant", "Esvaziar lixo" to "Vider la corbeille",
        "Esvaziar lixo?" to "Vider la corbeille ?", "Espaço quase esgotado" to "Espace presque plein",
        "Armazenamento" to "Stockage", "Menu" to "Menu", "Definições" to "Paramètres",
        "Grelha" to "Grille", "Lista" to "Liste", "Itens" to "Éléments", "Dono" to "Propriétaire",
        "Editor" to "Éditeur", "Apenas ver" to "Lecture seule", "Criado em" to "Créé le",
        "Atualizado em" to "Mis à jour le", "Expira em" to "Expire dans",
        "Desmarcar todos" to "Tout désélectionner", "Limpar selecção" to "Effacer la sélection",
        "Selecionar tudo" to "Tout sélectionner", "Mais usados" to "Les plus utilisés",
        "Arrasta aqui as tuas pastas favoritas" to "Glissez vos dossiers favoris ici",
        "Insere os URLs separados por vírgula ou nova linha" to "Saisissez les URL séparées par une virgule ou un retour à la ligne",
        "Perfil" to "Profil", "Nome e detalhes da conta" to "Nom et détails du compte",
        "Alterar a tua password" to "Changer votre mot de passe",
        "Espaço utilizado e disponível" to "Espace utilisé et disponible",
        "Sessões" to "Sessions", "Dispositivos e sessões activas" to "Appareils et sessions actives",
        "Faturação" to "Facturation", "Histórico de transações" to "Historique des transactions",
        "Segurança da app" to "Sécurité de l'app", "Biométrico e PIN" to "Biométrie et PIN",
        "Administração" to "Administration", "Painel" to "Tableau de bord",
        "Visão geral e estatísticas" to "Vue d'ensemble et statistiques",
        "Análises" to "Analyses", "Visitas e visitantes" to "Visites et visiteurs",
        "Utilizadores" to "Utilisateurs", "Lista de utilizadores" to "Liste des utilisateurs",
        "Registos de Convite" to "Inscriptions par invitation",
        "Convidar e gerir registos" to "Inviter et gérer les inscriptions",
        "Notícias" to "Actualités", "Notícia importante na zona de files" to "Actualité importante dans la zone fichiers",
        "Terminar sessão" to "Se déconnecter", "Sair da Conta?" to "Se déconnecter ?",
        "Tens a certeza que queres sair da tua conta?" to "Voulez-vous vraiment vous déconnecter ?",
        "Idioma" to "Langue",
        "Mostrar log" to "Afficher le journal", "Limpar cache local" to "Vider le cache local",
        "Registo de atividade" to "Journal d'activité", "Sem entradas" to "Aucune entrée",
        "Nada registado ainda" to "Rien d'enregistré pour l'instant",
        "Backups Automáticos" to "Sauvegardes automatiques", "Fazer backup agora" to "Sauvegarder maintenant",
        "A enviar…" to "Envoi…", "Itens copiados" to "Éléments sauvegardés",
        "Conteúdos a incluir" to "Contenus à inclure",
        "Imagens" to "Images", "Vídeos" to "Vidéos", "Áudios" to "Audios",
        "Documentos" to "Documents", "Downloads" to "Téléchargements", "Fotos" to "Photos",
        "Ficheiros" to "Fichiers", "Pastas disponíveis" to "Dossiers disponibles",
        "Dados móveis" to "Données mobiles", "Apenas a carregar" to "Uniquement en charge",
        "Voltar a verificar a galeria" to "Réanalyser la galerie",
        "Re-verificar a galeria" to "Réanalyser la galerie",
        "A ler pastas…" to "Lecture des dossiers…",
        "Sem pastas para esta categoria." to "Aucun dossier pour cette catégorie.",
        "Nenhuma pasta escolhida — toca para selecionar" to "Aucun dossier choisi — touchez pour sélectionner",
        "Backup desactivado." to "Sauvegarde désactivée.",
        "A eliminar…" to "Suppression…", "A mover…" to "Déplacement…",
        "Ativa uma categoria e escolhe as pastas do telemóvel que entram no backup." to
            "Activez une catégorie et choisissez les dossiers du téléphone à sauvegarder.",
    )

    private val es = mapOf(
        "Abrir" to "Abrir", "Adicionar" to "Añadir", "Descarregar" to "Descargar",
        "Detalhes" to "Detalles", "Eliminar" to "Eliminar",
        "Eliminar permanentemente" to "Eliminar permanentemente",
        "Eliminar permanentemente?" to "¿Eliminar permanentemente?",
        "Mover" to "Mover", "Mover para o lixo" to "Mover a la papelera",
        "Mover para o lixo?" to "¿Mover a la papelera?",
        "Partilhar" to "Compartir", "Editar partilha" to "Editar uso compartido",
        "Editar item" to "Editar elemento", "Renomear" to "Renombrar",
        "Restaurar" to "Restaurar", "Guardar" to "Guardar", "Cancelar" to "Cancelar",
        "Criar" to "Crear", "Fechar" to "Cerrar", "Feito" to "Hecho",
        "Seguinte" to "Siguiente", "Limpar" to "Limpiar", "Todas" to "Todas",
        "Voltar" to "Volver", "Sair" to "Salir", "Iniciar" to "Iniciar",
        "Activar" to "Activar", "Permitir" to "Permitir", "Tentar de novo" to "Reintentar",
        "Novo nome" to "Nuevo nombre", "Mais opções" to "Más opciones",
        "Partilhado" to "Compartido", "Sem conteúdo" to "Sin contenido",
        "Adicionar aos favoritos" to "Añadir a favoritos",
        "Remover dos favoritos" to "Quitar de favoritos",
        "Converter em pasta de equipa" to "Convertir en carpeta de equipo",
        "Pedido de ficheiros" to "Solicitud de archivos",
        "Os meus ficheiros" to "Mis archivos", "Carregamentos recentes" to "Subidas recientes",
        "Partilhado comigo" to "Compartido conmigo", "Favoritos" to "Favoritos",
        "Lixo" to "Papelera", "Lixeira" to "Papelera", "Pastas de equipa" to "Carpetas de equipo",
        "Esta pasta está vazia" to "Esta carpeta está vacía",
        "Não foi possível carregar" to "No se pudo cargar",
        "Criar pasta" to "Crear carpeta", "Criar pasta de equipa" to "Crear carpeta de equipo",
        "Criar pedido de ficheiros" to "Crear solicitud de archivos",
        "Carregar ficheiros" to "Subir archivos", "Carregar pasta" to "Subir carpeta",
        "Carregamento remoto" to "Subida remota", "Esvaziar lixo" to "Vaciar papelera",
        "Esvaziar lixo?" to "¿Vaciar papelera?", "Espaço quase esgotado" to "Espacio casi agotado",
        "Armazenamento" to "Almacenamiento", "Menu" to "Menú", "Definições" to "Ajustes",
        "Grelha" to "Cuadrícula", "Lista" to "Lista", "Itens" to "Elementos", "Dono" to "Propietario",
        "Editor" to "Editor", "Apenas ver" to "Solo ver", "Criado em" to "Creado el",
        "Atualizado em" to "Actualizado el", "Expira em" to "Caduca en",
        "Desmarcar todos" to "Deseleccionar todo", "Limpar selecção" to "Limpiar selección",
        "Selecionar tudo" to "Seleccionar todo", "Mais usados" to "Más usados",
        "Arrasta aqui as tuas pastas favoritas" to "Arrastra aquí tus carpetas favoritas",
        "Insere os URLs separados por vírgula ou nova linha" to "Introduce las URL separadas por coma o salto de línea",
        "Perfil" to "Perfil", "Nome e detalhes da conta" to "Nombre y detalles de la cuenta",
        "Alterar a tua password" to "Cambiar tu contraseña",
        "Espaço utilizado e disponível" to "Espacio usado y disponible",
        "Sessões" to "Sesiones", "Dispositivos e sessões activas" to "Dispositivos y sesiones activas",
        "Faturação" to "Facturación", "Histórico de transações" to "Historial de transacciones",
        "Segurança da app" to "Seguridad de la app", "Biométrico e PIN" to "Biometría y PIN",
        "Administração" to "Administración", "Painel" to "Panel",
        "Visão geral e estatísticas" to "Resumen y estadísticas",
        "Análises" to "Analíticas", "Visitas e visitantes" to "Visitas y visitantes",
        "Utilizadores" to "Usuarios", "Lista de utilizadores" to "Lista de usuarios",
        "Registos de Convite" to "Registros por invitación",
        "Convidar e gerir registos" to "Invitar y gestionar registros",
        "Notícias" to "Noticias", "Notícia importante na zona de files" to "Noticia importante en la zona de archivos",
        "Terminar sessão" to "Cerrar sesión", "Sair da Conta?" to "¿Cerrar sesión?",
        "Tens a certeza que queres sair da tua conta?" to "¿Seguro que quieres cerrar sesión?",
        "Idioma" to "Idioma",
        "Mostrar log" to "Mostrar registro", "Limpar cache local" to "Borrar caché local",
        "Registo de atividade" to "Registro de actividad", "Sem entradas" to "Sin entradas",
        "Nada registado ainda" to "Nada registrado todavía",
        "Backups Automáticos" to "Copias de seguridad automáticas", "Fazer backup agora" to "Hacer copia ahora",
        "A enviar…" to "Enviando…", "Itens copiados" to "Elementos copiados",
        "Conteúdos a incluir" to "Contenido a incluir",
        "Imagens" to "Imágenes", "Vídeos" to "Vídeos", "Áudios" to "Audios",
        "Documentos" to "Documentos", "Downloads" to "Descargas", "Fotos" to "Fotos",
        "Ficheiros" to "Archivos", "Pastas disponíveis" to "Carpetas disponibles",
        "Dados móveis" to "Datos móviles", "Apenas a carregar" to "Solo cargando",
        "Voltar a verificar a galeria" to "Volver a escanear la galería",
        "Re-verificar a galeria" to "Volver a escanear la galería",
        "A ler pastas…" to "Leyendo carpetas…",
        "Sem pastas para esta categoria." to "No hay carpetas para esta categoría.",
        "Nenhuma pasta escolhida — toca para selecionar" to "Ninguna carpeta elegida — toca para seleccionar",
        "Backup desactivado." to "Copia de seguridad desactivada.",
        "A eliminar…" to "Eliminando…", "A mover…" to "Moviendo…",
        "Ativa uma categoria e escolhe as pastas do telemóvel que entram no backup." to
            "Activa una categoría y elige las carpetas del teléfono que se incluyen en la copia.",
    )
}

/** Atalho: "Texto em PT".tr() devolve a tradução no idioma activo. */
fun String.tr(): String = I18n.t(this)
