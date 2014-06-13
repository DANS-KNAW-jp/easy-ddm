package nl.knaw.dans.easy.web.template;

import static org.apache.wicket.markup.html.CSSPackageResource.getHeaderContribution;

import org.apache.wicket.behavior.HeaderContributor;

public class Style
{
    public static final HeaderContributor //

            // style class related
            MODAL_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/modal/modal.css"),//
            LOGIN_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/login/login.css"),//
            FILE_EXPLORER_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/fileexplorer/file-explorer.css"),//
            RESET_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/reset.css"),//
            SEARCH_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/search.css"),//
            ADVANCED_SEARCH_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/advanced_search.css"),//
            DEPOSIT_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/deposit.css"),//
            USER_SELECTOR_HEADER_CONTRIBUTION = getHeaderContribution(Style.class, "css/user_selector.css"),//
            ACTIVITY_LOG_PANEL_CONTRIBUTION = getHeaderContribution(Style.class, "css/activity_log_panel.css"),//
            ADMIN_PANEL_CONTRIBUTION = getHeaderContribution(Style.class, "css/admin_panel.css"),//

            // webapp
            EASY_HEADER_CONTRIBUTION = getHeaderContribution("css/easy.css"),//
            EASY2_HEADER_CONTRIBUTION = getHeaderContribution("css2/easy.css"),//
            VIEW_DATASET_HEADER_CONTRIBUTION = getHeaderContribution("css/view_dataset.css");//
}