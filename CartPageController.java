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

import com.hpe.b2bstorefront.forms.FavoriteForm;
import com.hpe.controllers.ControllerConstants;
import com.hpe.facades.cart.action.FavoriteEntryActionFacade;
import com.hpe.facades.order.FavoriteFacade;
import com.hpe.facades.taxintegration.HPEB2BTaxintegrationFacade;
import de.hybris.platform.acceleratorfacades.cart.action.CartEntryAction;
import de.hybris.platform.acceleratorfacades.cart.action.CartEntryActionFacade;
import de.hybris.platform.acceleratorfacades.cart.action.exceptions.CartEntryActionException;
import de.hybris.platform.acceleratorfacades.csv.CsvFacade;
import de.hybris.platform.acceleratorfacades.flow.impl.SessionOverrideCheckoutFlowFacade;
import de.hybris.platform.acceleratorservices.controllers.page.PageType;
import de.hybris.platform.acceleratorservices.enums.CheckoutFlowEnum;
import de.hybris.platform.acceleratorservices.enums.CheckoutPciOptionEnum;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.Breadcrumb;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractCartPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.UpdateQuantityForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.VoucherForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.validation.SaveCartFormValidator;
import de.hybris.platform.acceleratorstorefrontcommons.util.XSSFilterUtil;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.order.data.*;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.quote.data.QuoteData;
import de.hybris.platform.commercefacades.voucher.VoucherFacade;
import de.hybris.platform.commercefacades.voucher.exceptions.VoucherOperationException;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.CommerceSaveCartException;
import de.hybris.platform.commerceservices.service.data.FavoriteSaveAction;
import de.hybris.platform.core.enums.QuoteState;
import de.hybris.platform.enumeration.EnumerationService;
import de.hybris.platform.site.BaseSiteService;
import de.hybris.platform.util.Config;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.hpe.controllers.pages.AccountFavoritesPageController.*;


/**
 * Controller for cart page
 */
@Controller
@RequestMapping(value = "/cart")
public class CartPageController extends AbstractCartPageController
{
	public static final String SHOW_CHECKOUT_STRATEGY_OPTIONS = "storefront.show.checkout.flows";
	public static final String ERROR_MSG_TYPE = "errorMsg";
	public static final String SUCCESSFUL_MODIFICATION_CODE = "success";
	public static final String VOUCHER_FORM = "voucherForm";
	public static final String SITE_QUOTES_ENABLED = "site.quotes.enabled.";
	private static final String CART_CHECKOUT_ERROR = "cart.checkout.error";
	private static final String CART_CMS_PAGE_LABEL = "cart.page.label";

	private static final String ACTION_CODE_PATH_VARIABLE_PATTERN = "{actionCode:.*}";
	private static final String CART_CODE_PATH_VARIABLE_PATTERN = "{cartCode:.*}";

	private static final String REDIRECT_CART_URL = REDIRECT_PREFIX + "/cart";
	private static final String REDIRECT_QUOTE_EDIT_URL = REDIRECT_PREFIX + "/quote/%s/edit/";
	private static final String REDIRECT_QUOTE_VIEW_URL = REDIRECT_PREFIX + "/my-account/my-quotes/%s/";
	private static final String REDIRECT_FAVORITE_LISTER_URL = REDIRECT_PREFIX + MY_ACCOUNT_SAVED_CARTS_URL;

	private static final Logger LOG = Logger.getLogger(CartPageController.class);

	@Resource(name = "simpleBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder resourceBreadcrumbBuilder;

	@Resource(name = "enumerationService")
	private EnumerationService enumerationService;

	@Resource(name = "productVariantFacade")
	private ProductFacade productFacade;

	@Resource(name = "saveCartFacade")
	private FavoriteFacade saveCartFacade;

	@Resource(name = "saveCartFormValidator")
	private SaveCartFormValidator saveCartFormValidator;

	@Resource(name = "csvFacade")
	private CsvFacade csvFacade;

	@Resource(name = "voucherFacade")
	private VoucherFacade voucherFacade;

	@Resource(name = "baseSiteService")
	private BaseSiteService baseSiteService;

	@Resource(name = "cartEntryActionFacade")
	private CartEntryActionFacade cartEntryActionFacade;


	@Resource(name = "favoriteEntryActionFacade")
	private FavoriteEntryActionFacade favoriteEntryActionFacade;

	@Resource(name = "accountFavoritesPageController")
	private AccountFavoritesPageController accountFavoritesPageController;

	@Resource(name = "hpeB2BTaxintegrationFacade")
	private HPEB2BTaxintegrationFacade hpeB2BTaxintegrationFacade;

	@ModelAttribute("showCheckoutStrategies")
	public boolean isCheckoutStrategyVisible()
	{
		return getSiteConfigService().getBoolean(SHOW_CHECKOUT_STRATEGY_OPTIONS, false);
	}

	@RequestMapping(method = RequestMethod.GET)
	public String showCart(final Model model, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		return prepareCartUrl(model, redirectModel, CART_CMS_PAGE_LABEL);
	}

	protected String prepareCartUrl(final Model model, final RedirectAttributes redirectModel, final String cmsPageLabel)
			throws CMSItemNotFoundException
	{
		if (isCreateFavorite(cmsPageLabel))
		{
			return displayCreateFavorite(model);
		}

		if (isFavoriteDetails(cmsPageLabel))
		{
			return accountFavoritesPageController.savedCart((String) model.asMap().get("cartCode"), model, redirectModel);
		}

		final Optional<String> quoteEditUrl = getQuoteUrl();
		if (quoteEditUrl.isPresent())
		{
			return quoteEditUrl.get();
		}
		else
		{
			prepareDataForPage(model);

			return ControllerConstants.Views.Pages.Cart.CartPage;
		}
	}

	protected Optional<String> getQuoteUrl()
	{
		final QuoteData quoteData = getCartFacade().getSessionCart().getQuoteData();

		return quoteData != null
				? (QuoteState.BUYER_OFFER.equals(quoteData.getState())
						? Optional.of(String.format(REDIRECT_QUOTE_VIEW_URL, urlEncode(quoteData.getCode())))
						: Optional.of(String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteData.getCode()))))
				: Optional.empty();
	}

	/**
	 * Handle the '/cart/checkout' request url. This method checks to see if the cart is valid before allowing the
	 * checkout to begin. Note that this method does not require the user to be authenticated and therefore allows us to
	 * validate that the cart is valid without first forcing the user to login. The cart will be checked again once the
	 * user has logged in.
	 *
	 * @return The page to redirect to
	 */
	@RequestMapping(value = "/checkout", method = RequestMethod.GET)
	@RequireHardLogIn
	public String cartCheck(final RedirectAttributes redirectModel)
	{
		SessionOverrideCheckoutFlowFacade.resetSessionOverrides();

		if (!getCartFacade().hasEntries())
		{
			LOG.info("Missing or empty cart");

			// No session cart or empty session cart. Bounce back to the cart page.
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "basket.error.checkout.empty.cart",
					null);
			return REDIRECT_CART_URL;
		}


		/*
		 * if (validateCart(redirectModel)) { GlobalMessages.addFlashMessage(redirectModel,
		 * GlobalMessages.ERROR_MESSAGES_HOLDER, CART_CHECKOUT_ERROR, null); return REDIRECT_CART_URL; }
		 */

		// Redirect to the start of the checkout flow to begin the checkout process
		// We just redirect to the generic '/checkout' page which will actually select the checkout flow
		// to use. The customer is not necessarily logged in on this request, but will be forced to login
		// when they arrive on the '/checkout' page.
		return REDIRECT_PREFIX + "/checkout";
	}

	@RequestMapping(value = "/getProductVariantMatrix", method = RequestMethod.GET)
	public String getProductVariantMatrix(@RequestParam("productCode") final String productCode,
			@RequestParam(value = "readOnly", required = false, defaultValue = "false") final String readOnly, final Model model)
	{

		final ProductData productData = productFacade.getProductForCodeAndOptions(productCode,
				Arrays.asList(ProductOption.BASIC, ProductOption.CATEGORIES, ProductOption.VARIANT_MATRIX_BASE,
						ProductOption.VARIANT_MATRIX_PRICE, ProductOption.VARIANT_MATRIX_MEDIA, ProductOption.VARIANT_MATRIX_STOCK,
						ProductOption.VARIANT_MATRIX_URL));

		model.addAttribute("product", productData);
		model.addAttribute("readOnly", Boolean.valueOf(readOnly));

		return ControllerConstants.Views.Fragments.Cart.ExpandGridInCart;
	}

	// This controller method is used to allow the site to force the visitor through a specified checkout flow.
	// If you only have a static configured checkout flow then you can remove this method.
	@RequestMapping(value = "/checkout/select-flow", method = RequestMethod.GET)
	@RequireHardLogIn
	public String initCheck(final Model model, final RedirectAttributes redirectModel,
			@RequestParam(value = "flow", required = false) final String flow,
			@RequestParam(value = "pci", required = false) final String pci)
	{
		SessionOverrideCheckoutFlowFacade.resetSessionOverrides();

		if (!getCartFacade().hasEntries())
		{
			LOG.info("Missing or empty cart");

			// No session cart or empty session cart. Bounce back to the cart page.
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "basket.error.checkout.empty.cart",
					null);
			return REDIRECT_CART_URL;
		}

		// Override the Checkout Flow setting in the session
		if (StringUtils.isNotBlank(flow))
		{
			final CheckoutFlowEnum checkoutFlow = enumerationService.getEnumerationValue(CheckoutFlowEnum.class,
					StringUtils.upperCase(flow));
			SessionOverrideCheckoutFlowFacade.setSessionOverrideCheckoutFlow(checkoutFlow);
		}

		// Override the Checkout PCI setting in the session
		if (StringUtils.isNotBlank(pci))
		{
			final CheckoutPciOptionEnum checkoutPci = enumerationService.getEnumerationValue(CheckoutPciOptionEnum.class,
					StringUtils.upperCase(pci));
			SessionOverrideCheckoutFlowFacade.setSessionOverrideSubscriptionPciOption(checkoutPci);
		}

		// Redirect to the start of the checkout flow to begin the checkout process
		// We just redirect to the generic '/checkout' page which will actually select the checkout flow
		// to use. The customer is not necessarily logged in on this request, but will be forced to login
		// when they arrive on the '/checkout' page.
		return REDIRECT_PREFIX + "/checkout";
	}

	@RequestMapping(value = "/entrygroups/{groupNumber}", method = RequestMethod.POST)
	public String removeGroup(@PathVariable("groupNumber") final Integer groupNumber, final Model model,
			final RedirectAttributes redirectModel)
	{
		final CartModificationData cartModification;
		try
		{
			cartModification = getCartFacade().removeEntryGroup(groupNumber);
			if (cartModification != null && !StringUtils.isEmpty(cartModification.getStatusMessage()))
			{
				GlobalMessages.addErrorMessage(model, cartModification.getStatusMessage());
			}
		}
		catch (final CommerceCartModificationException e)
		{
			LOG.error(e.getMessage(), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "basket.error.entrygroup.remove",
					new Object[]
					{ groupNumber });
		}
		return REDIRECT_CART_URL;
	}

	private boolean isCreateFavorite(final String cmsPageLabel)
	{
		return getSiteConfigService().getString(SAVED_CART_CREATE_PAGE_LABEL, "createFavorite").equalsIgnoreCase(cmsPageLabel);
	}


	protected boolean isFavoriteDetails(final String cmsPageLabel)
	{

		return getSiteConfigService().getString(SAVED_CART_DETAILS_PAGE_LABEL, "favoriteDetails").equalsIgnoreCase(cmsPageLabel);
	}

	@RequestMapping(value = "/update", method = RequestMethod.POST)
	public String updateCartQuantities(@RequestParam("entryNumber") final long entryNumber,
			@RequestParam("cartCode") final String cartCode, @Valid final UpdateQuantityForm form, final BindingResult bindingResult,
			final HttpServletRequest request,
			@RequestParam(value = "cmsPageLabel", required = false, defaultValue = CART_CMS_PAGE_LABEL) final String cmsPageLabel,
			final Model model, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				if ("typeMismatch".equals(error.getCode()))
				{
					GlobalMessages.addErrorMessage(model, "basket.error.quantity.invalid");
				}
				else
				{
					GlobalMessages.addErrorMessage(model, error.getDefaultMessage());
				}
			}
		}
		else if (isFavoriteDetails(cmsPageLabel) && saveCartFacade.hasEntries(cartCode))
		{
			try
			{
				final CartModificationData cartModification = saveCartFacade.updateCartEntry(cartCode, entryNumber,
						form.getQuantity().longValue());
				addFlashMessage(form, request, redirectModel, cartModification);

				// Redirect to the cart page on update success so that the browser doesn't re-post again
				model.addAttribute("cartCode", cartCode);
				return getCartPageRedirectUrl(model, redirectModel, cmsPageLabel);
			}
			catch (final CommerceCartModificationException ex)
			{
				LOG.warn("Couldn't update product for cart: " + cartCode + " - with the entry number: " + entryNumber + ".", ex);
			}
		}
		else if (getCartFacade().hasEntries())
		{
			try
			{
				final CartModificationData cartModification = getCartFacade().updateCartEntry(entryNumber,
						form.getQuantity().longValue());
				addFlashMessage(form, request, redirectModel, cartModification);

				// Redirect to the cart page on update success so that the browser doesn't re-post again
				model.addAttribute("cartCode", cartCode);
				return getCartPageRedirectUrl(model, redirectModel, cmsPageLabel);
			}
			catch (final CommerceCartModificationException ex)
			{
				LOG.warn("Couldn't update product with the entry number: " + entryNumber + ".", ex);
			}
		}

		// if could not update cart, display cart/quote page again with error
		model.addAttribute("cartCode", cartCode);
		return prepareCartUrl(model, redirectModel, cmsPageLabel);
	}

	@Override
	protected void prepareDataForPage(final Model model) throws CMSItemNotFoundException
	{
		super.prepareDataForPage(model);
		hpeB2BTaxintegrationFacade.getEstimatedTaxFromOneSource();

		if (!model.containsAttribute(VOUCHER_FORM))
		{
			model.addAttribute(VOUCHER_FORM, new VoucherForm());
		}

		// Because DefaultSiteConfigService.getProperty() doesn't set default boolean value for undefined property,
		// this property key was generated to use Config.getBoolean() method
		final String siteQuoteProperty = SITE_QUOTES_ENABLED.concat(getBaseSiteService().getCurrentBaseSite().getUid());
		model.addAttribute("siteQuoteEnabled", Config.getBoolean(siteQuoteProperty, Boolean.TRUE));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs("breadcrumb.cart"));
		model.addAttribute("pageType", PageType.CART.name());

        final UpdateQuantityForm updateQuantityForm = new UpdateQuantityForm();
        updateQuantityForm.setQuantity(0L);
        model.addAttribute("updateQuantityForm", updateQuantityForm);
	}

	protected void addFlashMessage(final UpdateQuantityForm form, final HttpServletRequest request,
			final RedirectAttributes redirectModel, final CartModificationData cartModification)
	{
		if (cartModification.getQuantity() == form.getQuantity().longValue())
		{
			// Success

			if (cartModification.getQuantity() == 0)
			{
				// Success in removing entry
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "basket.page.message.remove");
			}
			else
			{
				// Success in update quantity
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "basket.page.message.update");
			}
		}
		else if (cartModification.getQuantity() > 0)
		{
			// Less than successful
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"basket.page.message.update.reducedNumberOfItemsAdded.lowStock", new Object[]
					{ XSSFilterUtil.filter(cartModification.getEntry().getProduct().getName()), Long.valueOf(cartModification.getQuantity()), form.getQuantity(), request.getRequestURL().append(cartModification.getEntry().getProduct().getUrl()) });
		}
		else
		{
			if (StringUtils.isNotBlank(cartModification.getStatusMessage()))
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
						cartModification.getStatusMessage(), new Object[0]);
			}
			else
			{
				// No more stock available
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
						"basket.page.message.update.reducedNumberOfItemsAdded.noStock", new Object[]
						{ XSSFilterUtil.filter(cartModification.getEntry().getProduct().getName()), request.getRequestURL().append(cartModification.getEntry().getProduct().getUrl()) });
			}
		}
	}

	@SuppressWarnings("boxing")
	@ResponseBody
	@RequestMapping(value = "/updateMultiD", method = RequestMethod.POST)
	public CartData updateCartQuantitiesMultiD(@RequestParam("entryNumber") final Integer entryNumber,
			@RequestParam("productCode") final String productCode, final Model model, @Valid final UpdateQuantityForm form,
			final BindingResult bindingResult)
	{
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				if ("typeMismatch".equals(error.getCode()))
				{
					GlobalMessages.addErrorMessage(model, "basket.error.quantity.invalid");
				}
				else
				{
					GlobalMessages.addErrorMessage(model, error.getDefaultMessage());
				}
			}
		}
		else
		{
			try
			{
				final CartModificationData cartModification = getCartFacade()
						.updateCartEntry(getOrderEntryData(form.getQuantity(), productCode, entryNumber));
				if (cartModification.getStatusCode().equals(SUCCESSFUL_MODIFICATION_CODE))
				{
					GlobalMessages.addMessage(model, GlobalMessages.CONF_MESSAGES_HOLDER, cartModification.getStatusMessage(), null);
				}
				else if (!model.containsAttribute(ERROR_MSG_TYPE))
				{
					GlobalMessages.addMessage(model, GlobalMessages.ERROR_MESSAGES_HOLDER, cartModification.getStatusMessage(), null);
				}
			}
			catch (final CommerceCartModificationException ex)
			{
				LOG.warn("Couldn't update product with the entry number: " + entryNumber + ".", ex);
			}

		}
		return getCartFacade().getSessionCart();
	}

	@SuppressWarnings("boxing")
	protected OrderEntryData getOrderEntryData(final long quantity, final String productCode, final Integer entryNumber)
	{
		final OrderEntryData orderEntry = new OrderEntryData();
		orderEntry.setQuantity(quantity);
		orderEntry.setProduct(new ProductData());
		orderEntry.getProduct().setCode(productCode);
		orderEntry.setEntryNumber(entryNumber);
		return orderEntry;
	}

    @RequestMapping(value = "/save", method = RequestMethod.GET)
    @RequireHardLogIn
    public String displayCreateFavorite(final Model model) throws CMSItemNotFoundException
    {
        prepareDataForPage(model);
        model.addAttribute("favoriteForm", new FavoriteForm());
		model.addAttribute("hasCompanyAdminPrivilege", saveCartFacade.hasCompanyAdminPrivilege());
		model.addAttribute("canEdit", true);

		final List<Breadcrumb> breadcrumbs = resourceBreadcrumbBuilder.getBreadcrumbs(null);
		breadcrumbs.add(new Breadcrumb(MY_ACCOUNT_SAVED_CARTS_URL,
				getMessageSource().getMessage("text.account.savedCarts", null, getI18nService().getCurrentLocale()), null));
		breadcrumbs.add(new Breadcrumb("#",
				getMessageSource().getMessage("breadcrumb.create", null, getI18nService().getCurrentLocale()), null));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, breadcrumbs);

		model.addAttribute("favoriteSaveAction", FavoriteSaveAction.CREATE_FROM_CART.name());

		final ContentPageModel contentPage = getCmsPageService()
				.getPageForLabel(getSiteConfigService().getString(SAVED_CART_CREATE_PAGE_LABEL, "createFavorite"));
		storeCmsPageInModel(model, contentPage);
		storeContentPageTitleInModel(model, getPageTitleResolver().resolveContentPageTitle(contentPage.getTitle()));
		return getViewForPage(model);
	}


	@RequestMapping(value = "/save/" + ACTION_CODE_PATH_VARIABLE_PATTERN + "/"
			+ CART_CODE_PATH_VARIABLE_PATTERN, method = RequestMethod.POST)
	@RequireHardLogIn
	public String createFavorite(@PathVariable("actionCode") final String actionCode,
			@PathVariable("cartCode") final String cartCode, final FavoriteForm form, final Model model,
			final BindingResult bindingResult, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{

		saveCartFormValidator.validate(form, bindingResult);
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, error.getCode());
			}
			redirectModel.addFlashAttribute("favoriteForm", form);
			return displayCreateFavorite(model);
		}
		final FavoriteSaveAction action;
		try
		{
			action = FavoriteSaveAction.valueOf(actionCode);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error(String.format("Unknown favorite save action %s", actionCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"basket.page.favorite.unknownSaveAction");
			redirectModel.addFlashAttribute("favoriteForm", form);
			return displayCreateFavorite(model);
		}

		final CommerceSaveCartParameterData commerceSaveCartParameterData = new CommerceSaveCartParameterData();
		commerceSaveCartParameterData.setCartId(cartCode);
		commerceSaveCartParameterData.setName(form.getName());
		commerceSaveCartParameterData.setDescription(form.getDescription());
		commerceSaveCartParameterData.setEnableHooks(true);
		commerceSaveCartParameterData.setCompanyFavorite(form.isCompanyFavorite());
		commerceSaveCartParameterData.setSaveAction(action);
		try
		{
			final CommerceSaveCartResultData saveCartData = saveCartFacade.saveCart(commerceSaveCartParameterData);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "basket.save.cart.on.success",
					new Object[]
					{ saveCartData.getSavedCartData().getName() });
		}
		catch (final CommerceSaveCartException csce)
		{
			LOG.error(csce.getMessage(), csce);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "basket.save.cart.on.error",
					new Object[]
					{ form.getName() });
			return displayCreateFavorite(model);
		}


		return REDIRECT_FAVORITE_LISTER_URL;
	}

	@RequestMapping(value = "/export", method = RequestMethod.GET, produces = "text/csv")
	public String exportCsvFile(final HttpServletResponse response, final RedirectAttributes redirectModel) throws IOException
	{
		response.setHeader("Content-Disposition", "attachment;filename=cart.csv");

		try (final StringWriter writer = new StringWriter())
		{
			try
			{
				final List<String> headers = new ArrayList<String>();
				headers.add(getMessageSource().getMessage("basket.export.cart.item.sku", null, getI18nService().getCurrentLocale()));
				headers.add(
						getMessageSource().getMessage("basket.export.cart.item.quantity", null, getI18nService().getCurrentLocale()));
				headers.add(getMessageSource().getMessage("basket.export.cart.item.name", null, getI18nService().getCurrentLocale()));
				headers
						.add(getMessageSource().getMessage("basket.export.cart.item.price", null, getI18nService().getCurrentLocale()));

				final CartData cartData = getCartFacade().getSessionCartWithEntryOrdering(false);
				csvFacade.generateCsvFromCart(headers, true, cartData, writer);

				StreamUtils.copy(writer.toString(), StandardCharsets.UTF_8, response.getOutputStream());
			}
			catch (final IOException e)
			{
				LOG.error(e.getMessage(), e);
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "basket.export.cart.error", null);

				return REDIRECT_CART_URL;
			}

		}

		return null;
	}

	@RequestMapping(value = "/voucher/apply", method = RequestMethod.POST)
	public String applyVoucherAction(@Valid final VoucherForm form, final BindingResult bindingResult,
			final RedirectAttributes redirectAttributes)
	{
		try
		{
			if (bindingResult.hasErrors())
			{
				redirectAttributes.addFlashAttribute("errorMsg",
						getMessageSource().getMessage("text.voucher.apply.invalid.error", null, getI18nService().getCurrentLocale()));
			}
			else
			{
				voucherFacade.applyVoucher(form.getVoucherCode());
				redirectAttributes.addFlashAttribute("successMsg",
						getMessageSource().getMessage("text.voucher.apply.applied.success", new Object[]
						{ form.getVoucherCode() }, getI18nService().getCurrentLocale()));
			}
		}
		catch (final VoucherOperationException e)
		{
			redirectAttributes.addFlashAttribute(VOUCHER_FORM, form);
			redirectAttributes.addFlashAttribute("errorMsg",
					getMessageSource().getMessage(e.getMessage(), null,
							getMessageSource().getMessage("text.voucher.apply.invalid.error", null, getI18nService().getCurrentLocale()),
							getI18nService().getCurrentLocale()));
			if (LOG.isDebugEnabled())
			{
				LOG.debug(e.getMessage(), e);
			}

		}

		return REDIRECT_CART_URL;
	}

	@RequestMapping(value = "/voucher/remove", method = RequestMethod.POST)
	public String removeVoucher(@Valid final VoucherForm form, final RedirectAttributes redirectModel)
	{
		try
		{
			voucherFacade.releaseVoucher(form.getVoucherCode());
		}
		catch (final VoucherOperationException e)
		{
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "text.voucher.release.error",
					new Object[]
					{ form.getVoucherCode() });
			if (LOG.isDebugEnabled())
			{
				LOG.debug(e.getMessage(), e);
			}

		}
		return REDIRECT_CART_URL;
	}


	@Override
	public BaseSiteService getBaseSiteService()
	{
		return baseSiteService;
	}

	public void setBaseSiteService(final BaseSiteService baseSiteService)
	{
		this.baseSiteService = baseSiteService;
	}

	@RequestMapping(value = "/entry/execute/" + ACTION_CODE_PATH_VARIABLE_PATTERN, method = RequestMethod.POST)
	public String executeCartEntryAction(@PathVariable(value = "actionCode") final String actionCode,
			@RequestParam("cartCode") final String cartCode,
			@RequestParam(value = "cmsPageLabel", required = false, defaultValue = CART_CMS_PAGE_LABEL) final String cmsPageLabel,
			final RedirectAttributes redirectModel, final Model model, @RequestParam("entryNumbers") final Long[] entryNumbers)
			throws CMSItemNotFoundException
	{
		final CartEntryAction action;
		try
		{
			action = CartEntryAction.valueOf(actionCode);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error(String.format("Unknown cart entry action %s", actionCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "basket.page.entry.unknownAction");
			model.addAttribute("cartCode", cartCode);
			return getCartPageRedirectUrl(model, redirectModel, cmsPageLabel);
		}

		final CartEntryActionFacade cartEntryActionFacade = isFavoriteDetails(cmsPageLabel) ? favoriteEntryActionFacade
				: this.cartEntryActionFacade;
		try
		{
			final Optional<String> redirectUrl = isFavoriteDetails(cmsPageLabel)
					? favoriteEntryActionFacade.executeAction(action, cartCode, Arrays.asList(entryNumbers))
					: cartEntryActionFacade.executeAction(action, Arrays.asList(entryNumbers));
			final Optional<String> successMessageKey = cartEntryActionFacade.getSuccessMessageKey(action);
			if (successMessageKey.isPresent())
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, successMessageKey.get());
			}
			if (redirectUrl.isPresent())
			{
				return redirectUrl.get();
			}
			else
			{
				model.addAttribute("cartCode", cartCode);
				return getCartPageRedirectUrl(model, redirectModel, cmsPageLabel);
			}
		}
		catch (final CartEntryActionException e)
		{
			LOG.error(String.format("Failed to execute action %s", action), e);
			final Optional<String> errorMessageKey = cartEntryActionFacade.getErrorMessageKey(action);
			if (errorMessageKey.isPresent())
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, errorMessageKey.get());
			}
			model.addAttribute("cartCode", cartCode);
			return getCartPageRedirectUrl(model, redirectModel, cmsPageLabel);
		}
	}

	protected String getCartPageRedirectUrl(final Model model, final RedirectAttributes redirectModel, final String cmsPageLabel)
			throws CMSItemNotFoundException
	{
		if (isCreateFavorite(cmsPageLabel))
		{
			return displayCreateFavorite(model);
		}

		if (isFavoriteDetails(cmsPageLabel))
		{
			return accountFavoritesPageController.savedCart((String) model.asMap().get("cartCode"), model, redirectModel);
		}

		final QuoteData quoteData = getCartFacade().getSessionCart().getQuoteData();
		return quoteData != null ? String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteData.getCode())) : REDIRECT_CART_URL;
	}

    @Override
    public void createProductEntryList(final Model model, final CartData cartData) {
        super.createProductEntryList(model, cartData);
    }

	@RequestMapping(value = "/updateAll", method = RequestMethod.POST)
	public String updateCartQuantities(@RequestParam("cartCode") final String cartCode, @RequestParam("cmsPageLabel") final String cmsPageLabel,
									   @Valid final UpdateQuantityForm form, final BindingResult bindingResult, final HttpServletRequest request,
									   final Model model, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				if ("typeMismatch".equals(error.getCode()))
				{
					GlobalMessages.addErrorMessage(model, "basket.error.quantity.invalid");
				}
				else
				{
					GlobalMessages.addErrorMessage(model, error.getDefaultMessage());
				}
			}
		}
		else if (saveCartFacade.hasEntries(cartCode))
		{
			try
			{
				final Set<CartModificationData> cartModifications = saveCartFacade.updateCartEntries(cartCode, form.getQuantity().longValue());

				if (CollectionUtils.isNotEmpty(cartModifications)) {
					cartModifications.forEach(cartModification -> addFlashMessage(form, request, redirectModel, cartModification));
				}

				// Redirect to the favorite details / create favorite page on update success so that the browser doesn't re-post again
				model.addAttribute("cartCode", cartCode);
				return getCartPageRedirectUrl(model, redirectModel, cmsPageLabel);
			}
			catch (final CommerceCartModificationException ex)
			{
				LOG.warn("Couldn't update all items for cart: " + cartCode + " - with the quantity: " + form.getQuantity() + ".", ex);
			}
		}

		// if could not update favorite, display favorite details / create favorite page again with error
		model.addAttribute("cartCode", cartCode);
		return getCartPageRedirectUrl(model, redirectModel, cmsPageLabel);
	}

}