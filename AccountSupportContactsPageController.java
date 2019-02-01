/*
 * [y] hybris Platform
 *
 * Copyright (c) 2018 SAP SE or an SAP affiliate company. All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package com.hpe.controllers.pages;

import com.hpe.facades.b2bunit.HPEB2BUnitFacade;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.Breadcrumb;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import java.util.List;


/**
 * Controller for Organisational Support contacts.
 */
@Controller
@RequestMapping("/my-account/support-contacts")
public class AccountSupportContactsPageController extends AbstractSearchPageController
{

	private static final Logger LOG = Logger.getLogger(AccountSupportContactsPageController.class);

	private static final String ACCOUNT_SUPPORT_CONTACTS = "support-contacts";
    private static final String TEXT_SUPPORT_CONTACTS = "text.account.supportcontacts";

    @Resource(name = "b2bUnitFacade")
    private HPEB2BUnitFacade b2bUnitFacade;

	@Resource(name = "accountBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

	private final String[] DISALLOWED_FIELDS = new String[] {};

	@InitBinder
	public void init(final WebDataBinder binder)
	{
		binder.setDisallowedFields(DISALLOWED_FIELDS);
		binder.setBindEmptyMultipartFiles(false);
	}

	/**
	 * Lists all tickets
	 *
	 * @param model
	 * @return View String
	 * @throws CMSItemNotFoundException
	 */
	@RequestMapping(method = RequestMethod.GET)
	@RequireHardLogIn
	public String showSupportContacts(final Model model) throws CMSItemNotFoundException
	{
		model.addAttribute("supportContacts", b2bUnitFacade.getSupportContacts());

		final ContentPageModel contentPage = getContentPageForLabelOrId(ACCOUNT_SUPPORT_CONTACTS);
		storeCmsPageInModel(model, contentPage);
		setUpMetaDataForContentPage(model, contentPage);
        storeContentPageTitleInModel(model, getPageTitleResolver().resolveContentPageTitle(contentPage.getTitle()));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, getBreadcrumbs());
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);

		return getViewForPage(model);
	}

	private List<Breadcrumb> getBreadcrumbs()
	{
		final List<Breadcrumb> breadcrumbs = accountBreadcrumbBuilder.getBreadcrumbs(null);
		breadcrumbs.add(new Breadcrumb(
		        "/my-account/support-contacts", getMessageSource().getMessage(TEXT_SUPPORT_CONTACTS, null, getI18nService().getCurrentLocale()), null
        ));
		return breadcrumbs;
	}

}