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

import de.hybris.platform.acceleratorfacades.ordergridform.OrderGridFormFacade;
import de.hybris.platform.acceleratorfacades.product.data.ReadOnlyOrderGridData;
import de.hybris.platform.acceleratorservices.constants.GeneratedAcceleratorServicesConstants.Enumerations.ImportStatus;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.Breadcrumb;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractSearchPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.RestoreSaveCartForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.UpdateQuantityForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.validation.RestoreSaveCartFormValidator;
import de.hybris.platform.acceleratorstorefrontcommons.forms.validation.SaveCartFormValidator;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.order.CartFacade;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CommerceSaveCartParameterData;
import de.hybris.platform.commercefacades.order.data.CommerceSaveCartResultData;
import de.hybris.platform.commercefacades.order.data.FavoriteData;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commerceservices.order.CommerceSaveCartException;
import de.hybris.platform.commerceservices.service.data.FavoriteSaveAction;
import de.hybris.platform.core.GenericSearchConstants.LOG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hpe.b2bstorefront.forms.FavoriteForm;
import com.hpe.controllers.ControllerConstants;
import com.hpe.core.enums.FavoriteType;
import com.hpe.facades.order.FavoriteFacade;


/**
 * Controller for saved carts page
 */
@Controller
@RequestMapping("/my-account/favorites")
public class AccountFavoritesPageController extends AbstractSearchPageController
{
	protected static final String MY_ACCOUNT_SAVED_CARTS_URL = "/my-account/favorites";
	private static final String REDIRECT_TO_SAVED_CARTS_PAGE = REDIRECT_PREFIX + MY_ACCOUNT_SAVED_CARTS_URL;

	protected static final String SAVED_CART_CREATE_PAGE_LABEL = "favorite.create.page.label";
	protected static final String SAVED_CARTS_PAGE_LABEL = "favorite.lister.page.label";
	protected static final String SAVED_CART_DETAILS_PAGE_LABEL = "favorite.details.page.label";

	private static final String SAVED_CART_CODE_PATH_VARIABLE_PATTERN = "{cartCode:.*}";

	private static final Logger LOG = Logger.getLogger(AccountFavoritesPageController.class);

	@Resource(name = "accountBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

	@Resource(name = "saveCartFacade")
	private FavoriteFacade saveCartFacade;

	@Resource(name = "productVariantFacade")
	private ProductFacade productFacade;

	@Resource(name = "orderGridFormFacade")
	private OrderGridFormFacade orderGridFormFacade;

	@Resource(name = "saveCartFormValidator")
	private SaveCartFormValidator saveCartFormValidator;

	@Resource(name = "hpeCartFacade")
	private CartFacade cartFacade;

	@Resource(name = "restoreSaveCartFormValidator")
	private RestoreSaveCartFormValidator restoreSaveCartFormValidator;

	@Resource(name = "cartPageController")
	private CartPageController cartPageController;

	@RequestMapping(method = RequestMethod.GET)
	@RequireHardLogIn
	public String savedCarts(@RequestParam(value = "page", defaultValue = "0") final int page,
			@RequestParam(value = "show", defaultValue = "Page") final ShowMode showMode,
			@RequestParam(value = "sort", required = false) final String sortCode, final Model model) throws CMSItemNotFoundException
	{
		// Handle paged search results
		final List<FavoriteData> favorites = saveCartFacade.getFavoritesForCurrentUser();
		model.addAttribute("favorites", favorites);
		final String savedCartsPageLabel = getSiteConfigService().getString(SAVED_CARTS_PAGE_LABEL, "saved-carts");
		storeCmsPageInModel(model, getContentPageForLabelOrId(savedCartsPageLabel));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(savedCartsPageLabel));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, accountBreadcrumbBuilder.getBreadcrumbs("text.account.savedCarts"));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return getViewForPage(model);
	}

	private String populateSavedCartDetailsForDisplay(final String cartCode, final Model model, final RedirectAttributes redirectModel) {
        final CommerceSaveCartParameterData parameter = new CommerceSaveCartParameterData();
        parameter.setCartId(cartCode);

        final CommerceSaveCartResultData resultData;
        try {
            resultData = saveCartFacade.getCartForCodeAndCurrentUser(parameter);
        } catch (final CommerceSaveCartException csce) {
            LOG.warn("Attempted to load a saved cart that does not exist or is not visible", csce);
            GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "system.error.page.not.found", null);
            return REDIRECT_TO_SAVED_CARTS_PAGE;
        }

        final CartData cartData = resultData.getSavedCartData();
        if (ImportStatus.PROCESSING.equals(cartData.getImportStatus()))
        {
            return REDIRECT_TO_SAVED_CARTS_PAGE;
        }

        final FavoriteData favoriteData = (FavoriteData) cartData;
        model.addAttribute("savedCartData", favoriteData);

        final FavoriteForm favoriteForm = new FavoriteForm();
        favoriteForm.setDescription(favoriteData.getDescription());
        favoriteForm.setName(favoriteData.getName());
		favoriteForm.setType(favoriteData.getFavoriteType());
        favoriteForm.setCompanyFavorite(FavoriteType.COMPANY.getCode().equalsIgnoreCase(favoriteData.getFavoriteType()));
        favoriteForm.setStatus(favoriteData.getFavoriteStatus());
        model.addAttribute("favoriteForm", favoriteForm);

        cartPageController.createProductEntryList(model, favoriteData);
        model.addAttribute("hasCompanyAdminPrivilege", saveCartFacade.hasCompanyAdminPrivilege());
		model.addAttribute("canEdit", saveCartFacade.canEdit(favoriteData));

        final UpdateQuantityForm updateQuantityForm = new UpdateQuantityForm();
        updateQuantityForm.setQuantity(0L);
        model.addAttribute("updateQuantityForm", updateQuantityForm);

		final List<Breadcrumb> breadcrumbs = accountBreadcrumbBuilder.getBreadcrumbs(null);
		breadcrumbs.add(new Breadcrumb(MY_ACCOUNT_SAVED_CARTS_URL,
				getMessageSource().getMessage("text.account.savedCarts", null, getI18nService().getCurrentLocale()), null));
		breadcrumbs.add(new Breadcrumb("#", cartData.getName(), null));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, breadcrumbs);

        return null;
    }

	@RequestMapping(value = "/" + SAVED_CART_CODE_PATH_VARIABLE_PATTERN, method = RequestMethod.GET)
	@RequireHardLogIn
	public String savedCart(@PathVariable("cartCode") final String cartCode, final Model model,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		final String redirect = populateSavedCartDetailsForDisplay(cartCode, model, redirectModel);
        if (StringUtils.isNotBlank(redirect)) {
            return redirect;
        }

		final String savedCartDetailsPageLabel = getSiteConfigService().getString(SAVED_CART_DETAILS_PAGE_LABEL, "favoriteDetails");
		storeCmsPageInModel(model, getContentPageForLabelOrId(savedCartDetailsPageLabel));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(savedCartDetailsPageLabel));
		return getViewForPage(model);
	}

	@RequestMapping(value = "/" + SAVED_CART_CODE_PATH_VARIABLE_PATTERN + "/saveAsNew", method = RequestMethod.GET)
	@RequireHardLogIn
	public String cloneSavedCart(@PathVariable("cartCode") final String cartCode, final Model model, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
        final String redirect = populateSavedCartDetailsForDisplay(cartCode, model, redirectModel);
        if (StringUtils.isNotBlank(redirect)) {
            return redirect;
        }

        model.addAttribute("favoriteSaveAction", FavoriteSaveAction.CREATE_FROM_FAVORITE.name());

        final ContentPageModel contentPage = getCmsPageService().getPageForLabel(getSiteConfigService().getString(SAVED_CART_CREATE_PAGE_LABEL, "createFavorite"));
		storeCmsPageInModel(model, contentPage);
		storeContentPageTitleInModel(model, getPageTitleResolver().resolveContentPageTitle(contentPage.getTitle()));
		return getViewForPage(model);
	}

	@RequestMapping(value = "/uploadingCarts", method = RequestMethod.GET, produces = "application/json")
	@ResponseBody
	@RequireHardLogIn
	public List<CartData> getUploadingSavedCarts(@RequestParam("cartCodes") final List<String> cartCodes)
			throws CommerceSaveCartException
	{
		final List<CartData> result = new ArrayList<CartData>();
		for (final String cartCode : cartCodes)
		{
			final CommerceSaveCartParameterData parameter = new CommerceSaveCartParameterData();
			parameter.setCartId(cartCode);

			final CommerceSaveCartResultData resultData = saveCartFacade.getCartForCodeAndCurrentUser(parameter);
			final CartData cartData = resultData.getSavedCartData();

			if (ImportStatus.COMPLETED.equals(cartData.getImportStatus()))
			{
				result.add(cartData);
			}
		}

		return result;
	}

	@RequestMapping(value = "/" + SAVED_CART_CODE_PATH_VARIABLE_PATTERN
			+ "/getReadOnlyProductVariantMatrix", method = RequestMethod.GET)
	@RequireHardLogIn
	public String getProductVariantMatrixForResponsive(@PathVariable("cartCode") final String cartCode,
			@RequestParam("productCode") final String productCode, final Model model, final RedirectAttributes redirectModel)
	{
		try
		{
			final CommerceSaveCartParameterData parameter = new CommerceSaveCartParameterData();
			parameter.setCartId(cartCode);

			final CommerceSaveCartResultData resultData = saveCartFacade.getCartForCodeAndCurrentUser(parameter);
			final CartData cartData = resultData.getSavedCartData();

			final Map<String, ReadOnlyOrderGridData> readOnlyMultiDMap = orderGridFormFacade.getReadOnlyOrderGridForProductInOrder(
					productCode, Arrays.asList(ProductOption.BASIC, ProductOption.CATEGORIES), cartData);
			model.addAttribute("readOnlyMultiDMap", readOnlyMultiDMap);

			return ControllerConstants.Views.Fragments.Checkout.ReadOnlyExpandedOrderForm;
		}
		catch (final CommerceSaveCartException e)
		{
			LOG.warn("Attempted to load a saved cart that does not exist or is not visible", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "system.error.page.not.found", null);
			return REDIRECT_TO_SAVED_CARTS_PAGE + "/" + cartCode;
		}
	}

	@RequestMapping(value = "/" + SAVED_CART_CODE_PATH_VARIABLE_PATTERN + "/edit", method = RequestMethod.POST)
	@RequireHardLogIn
	public String savedCartEdit(@PathVariable("cartCode") final String cartCode, final FavoriteForm form,
			final BindingResult bindingResult, final RedirectAttributes redirectModel) {
		saveCartFormValidator.validate(form, bindingResult);
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, error.getCode());
			}
			redirectModel.addFlashAttribute("favoriteForm", form);
		}
		else
		{
			final CommerceSaveCartParameterData commerceSaveCartParameterData = new CommerceSaveCartParameterData();
			commerceSaveCartParameterData.setCartId(cartCode);
			commerceSaveCartParameterData.setName(form.getName());
			commerceSaveCartParameterData.setDescription(form.getDescription());
			commerceSaveCartParameterData.setEnableHooks(true);
			commerceSaveCartParameterData.setCompanyFavorite(form.isCompanyFavorite());
			commerceSaveCartParameterData.setSaveAction(FavoriteSaveAction.UPDATE);
			try
			{
				final CommerceSaveCartResultData saveCartData = saveCartFacade.saveCart(commerceSaveCartParameterData);
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER,
						"text.account.saveCart.edit.success", new Object[]
						{ saveCartData.getSavedCartData().getName() });
			}
			catch (final CommerceSaveCartException csce)
			{
				LOG.error(csce.getMessage(), csce);
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
						"text.account.saveCart.edit.error", new Object[]
						{ form.getName() });
			}
		}
		return REDIRECT_TO_SAVED_CARTS_PAGE + "/" + cartCode;
	}

	@RequestMapping(value = "/{cartId}/restore", method = RequestMethod.GET)
	@RequireHardLogIn
	public String restoreSaveCartForId(@PathVariable(value = "cartId") final String cartId, final Model model)
			throws CommerceSaveCartException
	{
		final CommerceSaveCartParameterData parameters = new CommerceSaveCartParameterData();
		parameters.setCartId(cartId);
		final CommerceSaveCartResultData commerceSaveCartResultData = saveCartFacade.getCartForCodeAndCurrentUser(parameters);
		final boolean hasSessionCart = cartFacade.hasEntries();
		model.addAttribute("hasSessionCart", hasSessionCart);
		if (hasSessionCart)
		{
			model.addAttribute("autoGeneratedName", System.currentTimeMillis());
		}
		model.addAttribute(commerceSaveCartResultData);
		return ControllerConstants.Views.Fragments.Account.SavedCartRestorePopup;
	}

	@RequireHardLogIn
	@RequestMapping(value = "/{cartId}/restore", method = RequestMethod.POST)
	public @ResponseBody String postRestoreSaveCartForId(@PathVariable(value = "cartId") final String cartId,
			final RestoreSaveCartForm restoreSaveCartForm, final BindingResult bindingResult)
	{
		try
		{
			restoreSaveCartFormValidator.validate(restoreSaveCartForm, bindingResult);
			if (bindingResult.hasErrors())
			{
				return getMessageSource().getMessage(bindingResult.getFieldError().getCode(), null,
						getI18nService().getCurrentLocale());
			}

			if (restoreSaveCartForm.getCartName() != null && !restoreSaveCartForm.isPreventSaveActiveCart()
					&& cartFacade.hasEntries())
			{
				final CommerceSaveCartParameterData commerceSaveActiveCart = new CommerceSaveCartParameterData();
				commerceSaveActiveCart.setCartId(cartFacade.getSessionCart().getCode());
				commerceSaveActiveCart.setName(restoreSaveCartForm.getCartName());
				commerceSaveActiveCart.setEnableHooks(true);
				commerceSaveActiveCart.setSaveAction(FavoriteSaveAction.UPDATE);
				saveCartFacade.saveCart(commerceSaveActiveCart);
			}

			final CommerceSaveCartParameterData commerceSaveCartParameterData = new CommerceSaveCartParameterData();
			commerceSaveCartParameterData.setCartId(cartId);
			commerceSaveCartParameterData.setEnableHooks(true);
			if (cartFacade.hasSessionCart()) {
				if (restoreSaveCartForm.isKeepRestoredCart()) {
					final CommerceSaveCartResultData cloneResultData = saveCartFacade.cloneSavedCart(commerceSaveCartParameterData);
					commerceSaveCartParameterData.setCartId(cloneResultData.getSavedCartData().getCode());
				}
			} else {
				if (restoreSaveCartForm.isKeepRestoredCart()) {
					commerceSaveCartParameterData.setSaveAction(FavoriteSaveAction.UPDATE);
					saveCartFacade.saveCart(commerceSaveCartParameterData);
				} else {
					deleteSaveCartForId(cartId);
				}
			}
			saveCartFacade.restoreSavedCart(commerceSaveCartParameterData);
		}
		catch (final CommerceSaveCartException ex)
		{
			LOG.error("Error while restoring the cart for cartId " + cartId + " because of " + ex);
			return getMessageSource().getMessage("text.restore.savedcart.error", null, getI18nService().getCurrentLocale());
		}
		return String.valueOf(HttpStatus.OK);
	}

	@RequestMapping(value = "/{cartId}/delete", method = RequestMethod.DELETE)
	@ResponseStatus(value = HttpStatus.OK)
	@RequireHardLogIn
	public @ResponseBody String deleteSaveCartForId(@PathVariable(value = "cartId") final String cartId)
	{
		try
		{
			final CommerceSaveCartParameterData parameters = new CommerceSaveCartParameterData();
			parameters.setCartId(cartId);
			saveCartFacade.flagForDeletion(cartId);
		}
		catch (final CommerceSaveCartException ex)
		{
			LOG.error("Error while deleting the saved cart with cartId " + cartId + " because of " + ex);
			return getMessageSource().getMessage("text.delete.savedcart.error", null, getI18nService().getCurrentLocale());
		}
		return String.valueOf(HttpStatus.OK);
	}
}
