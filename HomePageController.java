/*
 * [y] hybris Platform
 *
 * Copyright (c) 2018 SAP SE or an SAP affiliate company.  All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package com.hpe.controllers.pages;

import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.AbstractPageModel;
import de.hybris.platform.util.Config;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hpe.controllers.HPEB2BStorefrontConstant;
import com.hpe.facades.message.HpeMessageFacade;
import com.hpe.facades.message.data.HpeMessageData;


/**
 * Controller for home page
 */
@Controller
@RequestMapping("/")
public class HomePageController extends HPEAbstractPageController
{

	private static final Logger LOG = Logger.getLogger(HomePageController.class);
	private static final String LOGOUT = "logout";
	private static final String ACCOUNT_CONFIRMATION_SIGNOUT_TITLE = "account.confirmation.signout.title";
	private static final String ACCOUNT_CONFIRMATION_CLOSE_TITLE = "account.confirmation.close.title";
	private static final String REDIRECT_URL_ADD_DELIVERY_ADDRESS = REDIRECT_PREFIX + "/checkout/multi/delivery-address/add";

	@Resource(name = "hpeMessageFacade")
	private HpeMessageFacade hpeMessageFacade;

	@RequestMapping(method = RequestMethod.GET)
	public String home(@RequestParam(value = WebConstants.CLOSE_ACCOUNT, defaultValue = "false") final boolean closeAcc,
			@RequestParam(value = LOGOUT, defaultValue = "false") final boolean logout, final Model model,
			final RedirectAttributes redirectModel, final HttpServletRequest request) throws CMSItemNotFoundException
	{
		LOG.debug(" Entering home method...");

		if (logout)
		{
			String message = ACCOUNT_CONFIRMATION_SIGNOUT_TITLE;
			if (closeAcc)
			{
				message = ACCOUNT_CONFIRMATION_CLOSE_TITLE;
			}
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.INFO_MESSAGES_HOLDER, message);
			return REDIRECT_PREFIX + ROOT;
		}

		storeCmsPageInModel(model, getContentPageForLabelOrId(null));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(null));
		updatePageTitle(model, getContentPageForLabelOrId(null));
		model.addAttribute("metaRobots", Config.getParameter(HPEB2BStorefrontConstant.SEO_ROBOTS_TAG));
		model.addAttribute("eloqua", Config.getParameter(HPEB2BStorefrontConstant.ELOQUA_ADDRESS));
		model.addAttribute("storetest", Config.getParameter(HPEB2BStorefrontConstant.STORE_ENVIRONMENT_TEST));

		final List<HpeMessageData> listOfMessages = new ArrayList();
		model.addAttribute("listOfMessages", listOfMessages);

		/** return getViewForPage(model); **/
		return REDIRECT_PREFIX + "/my-account";
	}

	protected void updatePageTitle(final Model model, final AbstractPageModel cmsPage)
	{
		storeContentPageTitleInModel(model, getPageTitleResolver().resolveHomePageTitle(cmsPage.getTitle()));
	}
}