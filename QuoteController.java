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

import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractCartPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.QuoteDiscountForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.QuoteForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.UpdateQuantityForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.VoucherForm;
import de.hybris.platform.acceleratorstorefrontcommons.tags.Functions;
import de.hybris.platform.b2bcommercefacades.company.B2BUserFacade;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.commercefacades.comment.data.CommentData;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.QuoteFacade;
import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.CommerceCartMetadata;
import de.hybris.platform.commercefacades.order.data.CommerceSaveCartParameterData;
import de.hybris.platform.commercefacades.order.data.CommerceSaveCartResultData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.PriceDataFactory;
import de.hybris.platform.commercefacades.product.data.PriceDataType;
import de.hybris.platform.commercefacades.quote.data.QuoteData;
import de.hybris.platform.commercefacades.user.UserFacade;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CustomerData;
import de.hybris.platform.commercefacades.util.CommerceCartMetadataUtils;
import de.hybris.platform.commercefacades.voucher.VoucherFacade;
import de.hybris.platform.commercefacades.voucher.data.VoucherData;
import de.hybris.platform.commercefacades.voucher.exceptions.VoucherOperationException;
import de.hybris.platform.commerceservices.enums.QuoteAction;
import de.hybris.platform.commerceservices.order.CommerceQuoteAssignmentException;
import de.hybris.platform.commerceservices.order.CommerceQuoteExpirationTimeException;
import de.hybris.platform.commerceservices.order.CommerceSaveCartException;
import de.hybris.platform.commerceservices.order.exceptions.IllegalQuoteStateException;
import de.hybris.platform.commerceservices.order.exceptions.IllegalQuoteSubmitException;
import de.hybris.platform.commerceservices.order.exceptions.QuoteUnderThresholdException;
import de.hybris.platform.commerceservices.util.QuoteExpirationTimeUtils;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.servicelayer.exceptions.ModelSavingException;
import de.hybris.platform.servicelayer.exceptions.SystemException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.servicelayer.internal.model.impl.ItemModelCloneCreator.CannotCloneException;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.util.Config;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.SmartValidator;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import com.hpe.checkout.steps.validation.impl.HPEDefaultSendQuoteEmailFormValidator;
import com.hpe.controllers.ControllerConstants;
import com.hpe.facades.country.HPECountryFacade;
import com.hpe.facades.order.FavoriteFacade;
import com.hpe.facades.orders.account.data.HPEAccountCustomFieldsData;
import com.hpe.facades.orders.data.HPEOrdersAccountData;
import com.hpe.facades.orders.data.HPEOrdersData;
import com.hpe.facades.quote.data.HPEQuoteSendEmailFormData;
import com.hpe.facades.quote.impl.HPEDefaultQuoteFacade;
import com.hpe.facades.quote.integeration.HPEQuoteIntegrationFacade;
import com.hpe.facades.region.HPERegionFacade;
import com.hpe.facades.user.HPEUserFacade;
import com.hpe.quote.form.HPEOrdersAccountForm;
import com.hpe.quote.form.HPEOrdersBillAddressForm;
import com.hpe.quote.form.HPEOrdersShipAddressForm;
import com.hpe.quote.form.HPEQuoteSendEmailForm;
import com.hpe.quote.form.HpeOrdersForm;
import com.hpe.util.HPEB2bCompanyUtils;
import com.hpe.util.HPEOrderFormDataBindingHelper;
import com.hpe.util.QuoteExpirationTimeConverter;


/**
 * Controller for Quotes
 *
 *
 */
@SessionAttributes(
{ "hpeOrdersForm", "hpeOrdersData" })
@Controller
@RequestMapping(value = "/quote")

public class QuoteController extends AbstractCartPageController
{
	private static final String REDIRECT_CART_URL = REDIRECT_PREFIX + "/cart";
	private static final String REDIRECT_QUOTE_LIST_URL = REDIRECT_PREFIX + "/my-account/my-quotes/";
	private static final String REDIRECT_QUOTE_EDIT_URL = REDIRECT_PREFIX + "/quote/%s/edit/";
	private static final String REDIRECT_QUOTE_CREATE_VIEW_URL = REDIRECT_PREFIX + "/quote/%s/createHpeQuoteView";
	private static final String REDIRECT_EDIT_ACCOUNT_URL = REDIRECT_PREFIX + "/quote/editQuoteAccount/%s/";

	private static final String REDIRECT_QUOTE_DETAILS_URL = REDIRECT_PREFIX + "/my-account/my-quotes/%s/";
	private static final String QUOTE_EDIT_CMS_PAGE = "quoteEditPage";
	private static final String QUOTE_CREATE_HPE_QUOTE_CMS_PAGE = "quoteCreateHpeQuotePage";
	private static final String HPE_ORDER_EDITACCOUNT_CMS_PAGE_LABEL = "hpeOrderEditAccountPage";
	protected static final String QUOTE_CREATE_HPE_QUOTE_CMS_PAGE_LABEL = "quote.create.page.label";
	private static final String REDIRECT_HPE_SENDQUOTE_EMAIL_URL = REDIRECT_PREFIX + "/quote/%s/createHpeQuoteSummary";
	private static final String HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE = "hpeCreateSendQuoteViaEmailPage";

	private static final String CART_CODE_PATH_VARIABLE_PATTERN = "{cartCode:.*}";
	private static final String REDIRECT_SENDQUOTE_EMAIL_VIEW_URL = REDIRECT_PREFIX + "/quote/sendQuoteEmail";

	private static final String VOUCHER_FORM = "voucherForm";
	private static final String ALLOWED_ACTIONS = "allowedActions";
	private static final String DATE_FORMAT_KEY = "text.quote.dateformat";

	// localization properties
	private static final String PAGINATION_NUMBER_OF_COMMENTS = "quote.pagination.numberofcomments";
	private static final String QUOTE_EMPTY_CART_ERROR = "quote.cart.empty.error";
	private static final String QUOTE_CREATE_ERROR = "quote.create.error";
	private static final String QUOTE_REQUOTE_ERROR = "quote.requote.error";
	private static final String QUOTE_EDIT_LOCKED_ERROR = "quote.edit.locked";
	private static final String QUOTE_TEXT_CANCEL_SUCCESS = "text.quote.cancel.success";
	private static final String QUOTE_TEXT_NOT_CANCELABLE = "text.quote.not.cancelable";
	private static final String QUOTE_NOT_SUBMITABLE_ERROR = "text.quote.not.submitable";
	private static final String QUOTE_NEWCART_ERROR = "quote.newcart.error";
	private static final String QUOTE_NEWCART_SUCCESS = "quote.newcart.success";
	private static final String QUOTE_SAVE_CART_ERROR = "quote.save.cart.error";
	private static final String QUOTE_SUBMIT_ERROR = "quote.submit.error";
	private static final String QUOTE_SUBMIT_SUCCESS = "quote.submit.success";
	private static final String QUOTE_EXPIRED = "quote.state.expired";
	private static final String QUOTE_REJECT_INITIATION_REQUEST = "quote.reject.initiate.request";
	private static final String ORDERS_SHIPPING_ADDRESS = "OrdersUnitShipping";
	private static final String ORDERS_BILLING_ADDRESS = "OrdersUnitBilling";
	private static final String QUOTE_UPLOAD_DOCS_FILE_PATH = "media.read.dir";
	private static final String BREADCRUMB_QUOTE_CREATE = "breadcrumb.quote.create";
	public static final String COUNTRY_NAME = "CountryName";
	public static final String REGION_LIST = "RegionList";
	public static final String COUNTRY_CODE = "CountryCode";
	public static final String COUNTRY_USCODE = "US";
	private static final String SAVE_DIR = "sys_master";
	private static final int MAX_UPLOAD_SIZE = 5;

	private static final String QUOTE_DATA = "quoteData";
	private static final String HPE_ORDERS_FORM = "hpeOrdersForm";
	private static final String HPE_ORDERS_DATA = "hpeOrdersData";
	private static final String HPE_ORDERS_ACCOUNT_FORM = "hpeOrdersAccountForm";
	private static final String HPE_ACCOUNTQUOTE_DATA = "hpeAccountQuoteData";
	private static final String HPE_ORDERS_SHIPADDRESS_FORM = "hpeOrdersShipAddressForm";
	private static final String HPE_ORDERS_BILLADDRESS_FORM = "hpeOrdersBillAddressForm";
	private static final String HPE_ORDERS_SHIPPINGADDRESS_DATA = "hpeShipingAddressData";
	private static final String HPE_ORDERS_BILLINGADDRESS_DATA = "hpeBillingAddressData";
	private static final String HPE_CARTDATA = "cartData";
	private static final String QUOTE = "QUOTE";
	private static final String CHECKOUT = "CHECKOUT";
	public static final String QIDS_WRITECALL_SWITCHFLAG = "qidscall.switchflag";


	private static final Logger LOG = Logger.getLogger(QuoteController.class);

	@Resource(name = "simpleBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder resourceBreadcrumbBuilder;

	@Resource(name = "quoteFacade")
	private QuoteFacade quoteFacade;

	@Resource(name = "voucherFacade")
	private VoucherFacade voucherFacade;

	@Resource(name = "saveCartFacade")
	private FavoriteFacade saveCartFacade;

	@Resource
	private SmartValidator smartValidator;

	@Resource(name = "priceDataFactory")
	private PriceDataFactory priceDataFactory;

	@Resource(name = "b2bUserFacade")
	private B2BUserFacade b2bUserFacade;

	@Resource
	private UserService userService;

	@Resource(name = "userFacade")
	private UserFacade userFacade;

	@Resource(name = "customerFacade")
	private CustomerFacade customerFacade;

	@Resource(name = "i18NFacade")
	private I18NFacade i18NFacade;

	@Resource(name = "hpeUserFacade")
	private HPEUserFacade hpeUserFacade;

	@Resource(name = "hpeCountryFacade")
	private HPECountryFacade hpeCountryFacade;

	@Resource(name = "hpeRegionFacade")
	private HPERegionFacade hpeRegionFacade;

	@Resource(name = "hpeDefaultQuoteFacade")
	private HPEDefaultQuoteFacade hpeDefaultQuoteFacade;

	@Resource(name = "multipartResolver")
	private CommonsMultipartResolver multipartResolver;

	@Resource(name = "hpeQuoteIntegrationFacade")
	private HPEQuoteIntegrationFacade hpeQuoteIntegrationFacade;

	@Resource(name = "hpeDefaultSendQuoteEmailFormValidator")
	private HPEDefaultSendQuoteEmailFormValidator hpeDefaultSendQuoteEmailFormValidator;

	@Resource(name = "hpeB2bCompanyUtils")
	private HPEB2bCompanyUtils hpeB2bCompanyUtils;

	@Resource(name = "orderFormDataBindingHelper")
	private HPEOrderFormDataBindingHelper orderFormDataBindingHelper;

	/**
	 * Initiate a new quote based on session cart.
	 *
	 * @param redirectModel
	 * @return Mapping to quote page.
	 */
	@RequestMapping(value = "/" + CART_CODE_PATH_VARIABLE_PATTERN + "/createInitiateQuote", method = RequestMethod.GET)
	@RequireHardLogIn
	public String createInitiateQuote(@PathVariable("cartCode") final String cartCode, final RedirectAttributes redirectModel)
	{
		LOG.info("createInitiateQuote method is inside");
		try
		{
			if (!saveCartFacade.hasEntries(cartCode))
			{
				// No session cart or empty session cart. Bounce back to the cart page.
				LOG.info("Missing or empty cart");
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_EMPTY_CART_ERROR, null);
				return REDIRECT_CART_URL;
			}
			return String.format(REDIRECT_QUOTE_CREATE_VIEW_URL, urlEncode(cartCode));

		}
		catch (final IllegalArgumentException | CannotCloneException | ModelSavingException e)
		{
			LOG.error("Unable to create quote", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_CREATE_ERROR, null);
			return REDIRECT_CART_URL;
		}
	}

	/**
	 * create a new quote page
	 *
	 * @author shaaiee
	 * @param model
	 * @param redirectModel
	 * @param quoteCode
	 * @return Mapping to quotesummary page.
	 * @throws CMSItemNotFoundException
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/" + CART_CODE_PATH_VARIABLE_PATTERN + "/createHpeQuoteView", method = RequestMethod.GET)
	public String showCreateQuoteView(@PathVariable("cartCode") final String cartCode, final Model model,
			@Valid final HpeOrdersForm hpeOrdersForm, @Valid final HPEOrdersData hpeOrdersData,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException, CommerceSaveCartException
	{
		LOG.info("showCreateQuoteView method starts");
		// here we set the price dislaimer flag and message to model - business logic inside
		hpeB2bCompanyUtils.setPriceDisclaimerToModel(model);

		if (model.containsAttribute(HPE_ORDERS_FORM))
		{
			model.addAttribute(new HpeOrdersForm());
			model.addAttribute(new HPEOrdersData());
			final CustomerData customerData = customerFacade.getCurrentCustomer();
			final HPEOrdersAccountForm hpeOrdersAccountForm = orderFormDataBindingHelper.setOrdersAccountForm(hpeOrdersForm, model,
					customerData);
			final Map<String, String> customFieldsMap = hpeUserFacade.getB2BUnitAccountCustomFields();
			hpeOrdersAccountForm.setCustomFieldsUnitMap(customFieldsMap);
			orderFormDataBindingHelper.validateCustomFieldFormAttributes(hpeOrdersAccountForm, model);
			final HPEOrdersAccountData hpeAccountQuoteData = orderFormDataBindingHelper.createHpeAccountDataFromHpeForm(model,
					hpeOrdersData, hpeOrdersAccountForm);
			hpeOrdersData.setHpeOrdersAccountData(hpeAccountQuoteData);
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);

			final AddressData shippingAddressData = hpeUserFacade.getB2BUnitOrdersShipBillViewAddress(ORDERS_SHIPPING_ADDRESS);
			final HPEOrdersShipAddressForm hpeOrdersShipAddressForm = orderFormDataBindingHelper.setOrdersShipAddressForm(model,
					hpeOrdersForm, shippingAddressData);
			hpeOrdersForm.setHpeOrdersShipAddressForm(hpeOrdersShipAddressForm);
			final AddressData hpeShipingAddressData = orderFormDataBindingHelper.createOrdersShipAddressData(model,
					hpeOrdersShipAddressForm, i18NFacade);
			hpeOrdersData.setHpeShipingAddressData(hpeShipingAddressData);
			final AddressData billingAddressData = hpeUserFacade.getB2BUnitOrdersShipBillViewAddress(ORDERS_BILLING_ADDRESS);
			final HPEOrdersBillAddressForm hpeOrdersBillAddressForm = orderFormDataBindingHelper.setOrdersBillAddressForm(model,
					hpeOrdersForm, billingAddressData);
			hpeOrdersForm.setHpeOrdersBillAddressForm(hpeOrdersBillAddressForm);
			final AddressData hpeBillingAddressData = orderFormDataBindingHelper.createOrdersBillAddressData(model,
					hpeOrdersBillAddressForm, i18NFacade);
			hpeOrdersData.setHpeBillingAddressData(hpeBillingAddressData);

			setQuoteCartDataInfoView(cartCode, hpeOrdersForm, hpeOrdersData, model);
			model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
			model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		LOG.info("showCreateQuoteView method is ends-----getViewForPage---->");

		return ControllerConstants.Views.Pages.Quote.HpeQuoteCreateAccountPage;
	}

	private void assignedHPEQuoteDataToForm(final String quoteCode, final HpeOrdersForm hpeOrdersForm,
			final HPEOrdersData hpeOrdersData)
	{
		//form- QuoteNumber
		if (StringUtils.isNotEmpty(quoteCode))
		{
			hpeOrdersForm.setQuoteNumber(quoteCode);
		}
		//QuoteName
		if (StringUtils.isNotEmpty(hpeOrdersForm.getQuoteName()))
		{
			hpeOrdersForm.setQuoteName(hpeOrdersForm.getQuoteName());
		}
		//Data
		if (StringUtils.isNotEmpty(hpeOrdersForm.getQuoteNumber()))
		{
			hpeOrdersData.setQuoteNumber(hpeOrdersForm.getQuoteNumber());
		}
		if (StringUtils.isNotEmpty(hpeOrdersForm.getQuoteName()))
		{
			hpeOrdersData.setQuoteName(hpeOrdersForm.getQuoteName());
		}
	}


	/**
	 * create quote summary page.
	 *
	 * @author shaaiee
	 * @param model
	 * @param redirectModel
	 * @param quoteCode
	 * @return Mapping to edit page.
	 * @throws CMSItemNotFoundException
	 */

	@RequestMapping(value = "/" + CART_CODE_PATH_VARIABLE_PATTERN + "/createHpeQuoteSummary", method =
	{ RequestMethod.GET, RequestMethod.POST })
	@RequireHardLogIn
	public String showCreateHpeQuoteSummary(@PathVariable("cartCode") final String cartCode,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model, final RedirectAttributes redirectModel,
			@ModelAttribute final HPEOrdersData hpeOrdersData, final SessionStatus sessionStatus, final BindingResult result)
			throws CMSItemNotFoundException
	{

		// here we set the price dislaimer flag and message to model - business logic inside
		hpeB2bCompanyUtils.setPriceDisclaimerToModel(model);

		String quoteCodeNew = "";
		String quoteCode = "";

		final QuoteData quoteData;
		if (saveCartFacade.isSessionCart(cartCode))
		{
			quoteData = getQuoteFacade().initiateQuote();
		}
		else
		{
			quoteData = getHpeDefaultQuoteFacade().initiateQuote(cartCode);
		}

		quoteCode = quoteData.getCode();
		assignedHPEQuoteDataToForm(quoteCode, hpeOrdersForm, hpeOrdersData);
		if (hpeOrdersForm != null)
		{
			if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
			{
				final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
				orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
				hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
				model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersAccountForm);
			}
			if (hpeOrdersForm.getHpeOrdersShipAddressForm() != null)
			{
				final HPEOrdersShipAddressForm hpeQuoteShipAddressForm = hpeOrdersForm.getHpeOrdersShipAddressForm();
				hpeOrdersForm.setHpeOrdersShipAddressForm(hpeQuoteShipAddressForm);
				model.addAttribute(HPE_ORDERS_SHIPADDRESS_FORM, hpeQuoteShipAddressForm);
				final AddressData hpeShipingAddressData = orderFormDataBindingHelper.createOrdersShipAddressData(model,
						hpeQuoteShipAddressForm, i18NFacade);
				hpeOrdersData.setHpeShipingAddressData(hpeShipingAddressData);
				model.addAttribute(HPE_ORDERS_SHIPPINGADDRESS_DATA, hpeShipingAddressData);
			}

			if (hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
			{
				final HPEOrdersBillAddressForm hpeOrdersBillAddressForm = hpeOrdersForm.getHpeOrdersBillAddressForm();
				hpeOrdersForm.setHpeOrdersBillAddressForm(hpeOrdersBillAddressForm);
				model.addAttribute(HPE_ORDERS_BILLADDRESS_FORM, hpeOrdersBillAddressForm);
				final AddressData hpeBillingAddressData = orderFormDataBindingHelper.createOrdersBillAddressData(model,
						hpeOrdersBillAddressForm, i18NFacade);
				hpeOrdersData.setHpeBillingAddressData(hpeBillingAddressData);
				model.addAttribute(HPE_ORDERS_BILLINGADDRESS_DATA, hpeBillingAddressData);
			}
			setQuoteExpiryDataInfo(hpeOrdersForm, hpeOrdersData, model);
			prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);
			try
			{
				final Collection<MediaModel> quoteMediaModelList = hpeOrdersForm.getHpeOrdersAccountForm().getQuoteMediaModelList();
				final QuoteData quoteDataUpdate = hpeDefaultQuoteFacade.saveQuoteAddressDataToQuoteModel(hpeOrdersData,
						quoteMediaModelList);
				quoteCodeNew = quoteDataUpdate.getCode();

				model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
				model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
				assignedQuoteSummaryToFormView(model, quoteCodeNew, hpeOrdersForm, hpeOrdersData, sessionStatus);
			}
			catch (final Exception e)
			{
				if (hpeOrdersForm != null)
				{
					sessionStatus.setComplete();
				}
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_SUBMIT_ERROR);
				LOG.debug("Create Quote saved id failed " + hpeOrdersForm.getQuoteNumber() + "== " + e.getMessage());
			}
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));

		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);

		GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, QUOTE_SUBMIT_SUCCESS, new Object[]
		{ quoteCodeNew });
		sessionStatus.setComplete();
		LOG.info("showCreateHpeQuoteSummaryNew method ENDS----");
		return ControllerConstants.Views.Pages.Quote.HpeQuoteCreateAccountSummaryPage;
	}



	/***
	 * Get the QuoteDtails in Summary Page
	 *
	 * @param model
	 * @param quoteCodeNew
	 * @param hpeOrdersForm
	 * @param hpeOrdersData
	 * @param sessionStatus
	 */
	private void assignedQuoteSummaryToFormView(final Model model, final String quoteCodeNew, final HpeOrdersForm hpeOrdersForm,
			final HPEOrdersData hpeOrdersData, final SessionStatus sessionStatus)
	{
		try
		{
			final QuoteData quoteData = hpeDefaultQuoteFacade.getHpeQuoteModel(quoteCodeNew);
			orderFormDataBindingHelper.getHPEQuoteDetails(quoteData, model);
			model.addAttribute(QUOTE_DATA, quoteData);
			hpeOrdersForm.setQuoteNumber(quoteData.getCode());
			hpeOrdersForm.setQuoteName(quoteData.getName());
			hpeOrdersData.setQuoteNumber(hpeOrdersForm.getQuoteNumber());
			hpeOrdersData.setQuoteName(hpeOrdersForm.getQuoteName());
			model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
			model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
		}
		catch (final Exception ex)
		{
			LOG.debug("Quote Model details getting error  " + quoteCodeNew + "== " + ex.getMessage());

		}
		final boolean isQuidsSwitchPropertyFlag = Config.getBoolean(QIDS_WRITECALL_SWITCHFLAG, Boolean.TRUE);
		LOG.debug("isQuidsSwitchPropertyFlag: " + isQuidsSwitchPropertyFlag);
		if (isQuidsSwitchPropertyFlag)
		{
			try
			{
				final String writeQuoteStrResponse = hpeQuoteIntegrationFacade
						.populateWriteQuoteRequestToQIDS(hpeOrdersForm.getQuoteNumber());
				LOG.debug("writeQuoteStrResponse: " + writeQuoteStrResponse);
			}
			catch (final Exception ex)
			{
				LOG.debug("QIDS Integeration WriteQuote Service call is failed  " + hpeOrdersForm.getQuoteNumber() + "== "
						+ ex.getMessage());
			}
		}

	}



	private HPEOrdersAccountData populateFormAccountToData(final Model model, final HPEOrdersAccountForm hpeOrdersAccountForm,
			final HPEOrdersData hpeOrdersData)
	{
		final HPEOrdersAccountData hpeAccountQuoteData = new HPEOrdersAccountData();
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getPhone()))
		{
			hpeAccountQuoteData.setPhone(hpeOrdersAccountForm.getPhone());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getFirstName()))
		{
			hpeAccountQuoteData.setFirstName(hpeOrdersAccountForm.getFirstName());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getLastName()))
		{
			hpeAccountQuoteData.setLastName(hpeOrdersAccountForm.getLastName());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getAccountUidEmail()))
		{
			hpeAccountQuoteData.setAccountUidEmail(hpeOrdersAccountForm.getAccountUidEmail());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getAccountB2BCompany()))
		{
			hpeAccountQuoteData.setAccountB2BCompany(hpeOrdersAccountForm.getAccountB2BCompany());
		}
		if (hpeOrdersAccountForm.getUploadQuoteFile() != null)
		{
			hpeAccountQuoteData.setUploadOrdersFile(hpeOrdersAccountForm.getUploadQuoteFile());
		}
		if (hpeOrdersAccountForm.getUploadQuoteFilesList() != null)
		{
			hpeAccountQuoteData.setUploadOrdersFilesList(hpeOrdersAccountForm.getUploadQuoteFilesList());
		}
		if (hpeOrdersAccountForm.getUploadQuoteFileCount() != 0)
		{
			hpeAccountQuoteData.setUploadOrdersFileCount(hpeOrdersAccountForm.getUploadQuoteFileCount());
		}
		//media
		if (hpeOrdersAccountForm.getQuoteMediaModelList() != null)
		{
			hpeAccountQuoteData.setOrdersMediaModelList(hpeOrdersAccountForm.getQuoteMediaModelList());
		}
		if (hpeOrdersAccountForm.getQuoteMediaPK() != null)
		{
			hpeAccountQuoteData.setOrdersMediaPK(hpeOrdersAccountForm.getQuoteMediaPK());
		}
		if (hpeOrdersAccountForm.getQuoteMediaURL() != null)
		{
			hpeAccountQuoteData.setOrdersMediaUrlLink(hpeOrdersAccountForm.getQuoteMediaURL());
		}
		if (hpeOrdersAccountForm.getMediaFileUrlMap() != null)
		{
			hpeAccountQuoteData.setOrdersMediaFileDataUrlMap(hpeOrdersAccountForm.getMediaFileUrlMap());
		}
		if (hpeOrdersAccountForm.getCustomFieldsQuoteMap() != null && hpeOrdersAccountForm.getCustomFieldsQuoteMap().size() > 0)
		{
			hpeAccountQuoteData.setCustomFieldsOrdersMapData(hpeOrdersAccountForm.getCustomFieldsQuoteMap());
		}
		if (hpeOrdersAccountForm.getCustomFieldsFormList() != null && hpeOrdersAccountForm.getCustomFieldsFormList().size() > 0)
		{
			hpeAccountQuoteData.setHpeAcctCustomFieldsData(hpeOrdersAccountForm.getCustomFieldsFormList());

		}
		hpeOrdersData.setHpeOrdersAccountData(hpeAccountQuoteData);
		model.addAttribute(HPE_ACCOUNTQUOTE_DATA, hpeAccountQuoteData);

		return hpeAccountQuoteData;
	}


	/**
	 * Upload the Quote Account Upload files list
	 *
	 * @author SHAAIEE
	 * @param model
	 * @param httpServletRequest
	 * @param httpServletResponse
	 * @param files
	 * @param hpeOrdersForm
	 * @param result
	 * @return
	 */
	@RequestMapping(value = "/uploadAccountQuoteFiles", method =
	{ RequestMethod.GET, RequestMethod.POST })
	public String uploadAccountMultipleFileHandler(final Model model, @RequestParam("uploadQuoteFile") final MultipartFile[] files,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, @ModelAttribute final HPEOrdersData hpeOrdersData,
			final BindingResult result, final RedirectAttributes redirectModel, final HttpServletRequest request,
			final HttpServletResponse response)
	{
		if (result.hasErrors())
		{
			return "Upload the QuoteAccount Document Error";
		}

		String acctUserUid = "";
		int uploadFileCounter = 0;
		for (int count = 0; count < files.length; count++)
		{
			final MultipartFile multiPartfile = files[count];
			try
			{
				final byte[] bytes = multiPartfile.getBytes();
				final StringBuilder filePath = new StringBuilder(Config.getParameter(QUOTE_UPLOAD_DOCS_FILE_PATH));

				filePath.append(File.separator + SAVE_DIR);
				filePath.append(File.separator + new SimpleDateFormat("dd-MM-yyyy").format(new Date()));
				final String savePath = filePath.toString();
				LOG.info("savePath====media=====" + savePath);
				final File fileSaveDir = new File(filePath.toString());
				if (!fileSaveDir.exists())
				{
					fileSaveDir.mkdir();
				}
				// Create the file on server
				final File serverFile = new File(
						fileSaveDir.getAbsolutePath() + File.separator + multiPartfile.getOriginalFilename());
				final BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(serverFile));
				stream.write(bytes);
				stream.close();
				final String fileName = multiPartfile.getOriginalFilename();

				MediaModel mediaModel = null;
				String quoteMediaPK = null;
				String quoteMediaUrl = null;
				final List<MultipartFile> multiPartfilesList = new ArrayList<MultipartFile>();
				final Collection<MediaModel> quoteMediaModelList = new ArrayList<MediaModel>();
				final Map<MultipartFile, String> mediaFileUrlMap = new HashMap<MultipartFile, String>();

				if (hpeOrdersForm.getHpeOrdersAccountForm().getMediaFileUrlMap() == null)
				{
					hpeOrdersForm.getHpeOrdersAccountForm().setUploadQuoteFile(multiPartfile);
					multiPartfilesList.add(multiPartfile);
					hpeOrdersForm.getHpeOrdersAccountForm().setUploadQuoteFilesList(multiPartfilesList);
					uploadFileCounter = multiPartfilesList.size();
					hpeOrdersForm.getHpeOrdersAccountForm().setUploadQuoteFileCount(multiPartfilesList.size());

					mediaModel = hpeDefaultQuoteFacade.saveQuoteDocumentMedia(serverFile, fileName, savePath, uploadFileCounter);
					quoteMediaPK = mediaModel.getPk().toString();
					hpeOrdersForm.getHpeOrdersAccountForm().setQuoteMediaPK(quoteMediaPK);

					quoteMediaModelList.add(mediaModel);
					hpeOrdersForm.getHpeOrdersAccountForm().setQuoteMediaModelList(quoteMediaModelList);
					quoteMediaUrl = hpeDefaultQuoteFacade.getQuoteDocumentMediaUrl(mediaModel);
					orderFormDataBindingHelper.setQuoteMediaUrlResponse(mediaModel, response);
					quoteMediaUrl = orderFormDataBindingHelper.getBasePath(request) + quoteMediaUrl;
					hpeOrdersForm.getHpeOrdersAccountForm().setQuoteMediaURL(quoteMediaUrl);

					mediaFileUrlMap.put(multiPartfile, quoteMediaUrl);
					hpeOrdersForm.getHpeOrdersAccountForm().setMediaFileUrlMap(mediaFileUrlMap);
				}
				else if (hpeOrdersForm.getHpeOrdersAccountForm().getMediaFileUrlMap() != null
						&& hpeOrdersForm.getHpeOrdersAccountForm().getMediaFileUrlMap().size() > 0)
				{

					if (!hpeOrdersForm.getHpeOrdersAccountForm().getMediaFileUrlMap().containsKey(multiPartfile)
							&& !hpeOrdersForm.getHpeOrdersAccountForm().getUploadQuoteFile().equals(multiPartfile))
					{

						if (hpeOrdersForm.getHpeOrdersAccountForm().getMediaFileUrlMap().size() <= MAX_UPLOAD_SIZE)
						{
							final List<MultipartFile> multiPartfilesListExist = hpeOrdersForm.getHpeOrdersAccountForm()
									.getUploadQuoteFilesList();

							multiPartfilesList.addAll(multiPartfilesListExist);
							multiPartfilesList.add(multiPartfile);
							hpeOrdersForm.getHpeOrdersAccountForm().setUploadQuoteFilesList(multiPartfilesList);
							hpeOrdersForm.getHpeOrdersAccountForm().setUploadQuoteFile(multiPartfile);
							uploadFileCounter = multiPartfilesList.size();
							hpeOrdersForm.getHpeOrdersAccountForm().setUploadQuoteFileCount(multiPartfilesList.size());
							mediaModel = hpeDefaultQuoteFacade.saveQuoteDocumentMedia(serverFile, fileName, savePath, uploadFileCounter);
							quoteMediaPK = mediaModel.getPk().toString();
							hpeOrdersForm.getHpeOrdersAccountForm().setQuoteMediaPK(quoteMediaPK);

							final Collection<MediaModel> quoteMediaModelListExist = hpeOrdersForm.getHpeOrdersAccountForm()
									.getQuoteMediaModelList();
							quoteMediaModelList.addAll(quoteMediaModelListExist);
							quoteMediaModelList.add(mediaModel);
							hpeOrdersForm.getHpeOrdersAccountForm().setQuoteMediaModelList(quoteMediaModelList);

							quoteMediaUrl = hpeDefaultQuoteFacade.getQuoteDocumentMediaUrl(mediaModel);
							orderFormDataBindingHelper.setQuoteMediaUrlResponse(mediaModel, response);
							quoteMediaUrl = orderFormDataBindingHelper.getBasePath(request) + quoteMediaUrl;
							hpeOrdersForm.getHpeOrdersAccountForm().setQuoteMediaURL(quoteMediaUrl);


							final Map<MultipartFile, String> mediaFileUrlMapExist = hpeOrdersForm.getHpeOrdersAccountForm()
									.getMediaFileUrlMap();
							mediaFileUrlMap.putAll(mediaFileUrlMapExist);
							mediaFileUrlMap.put(multiPartfile, quoteMediaUrl);
							hpeOrdersForm.getHpeOrdersAccountForm().setMediaFileUrlMap(mediaFileUrlMap);
						}
						else if (hpeOrdersForm.getHpeOrdersAccountForm().getMediaFileUrlMap().size() > MAX_UPLOAD_SIZE)
						{
							GlobalMessages.addFlashMessage(redirectModel,
									"Max upload will be five of any file!..Upload the QuoteAccount Document Error!", null);
							redirectModel.addFlashAttribute("maxuploadmessage",
									" Max upload will be five of any file!..Upload the QuoteAccount Document Error!");
							break;
						}

					}
				}
				hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersForm.getHpeOrdersAccountForm());
				model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersForm.getHpeOrdersAccountForm());
				model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
			}
			catch (final Exception e)
			{
				LOG.info("You failed to upload " + multiPartfile.getOriginalFilename() + " => " + e.getMessage());
			}
		}
		if (hpeOrdersForm.getHpeOrdersAccountForm().getAccountUidEmail() != null)
		{
			acctUserUid = hpeOrdersForm.getHpeOrdersAccountForm().getAccountUidEmail();
		}
		return String.format(REDIRECT_EDIT_ACCOUNT_URL, urlEncode(acctUserUid));
	}

	/**
	 * Method to edit the Quote Account Information and With upload the document.
	 *
	 * @param hpeOrdersForm
	 * @param model
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException
	 */


	@RequestMapping(value = "/editQuoteAccount/{acctUserUid}", method =
	{ RequestMethod.GET, RequestMethod.POST })
	@RequireHardLogIn
	public String editQuoteAccountInfo(@PathVariable("acctUserUid") final String acctUserUid,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model, final RedirectAttributes redirectModel,
			@ModelAttribute final HPEOrdersData hpeOrdersData) throws CMSItemNotFoundException
	{
		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			if (hpeOrdersAccountForm.getAccountUidEmail().equals(acctUserUid))
			{
				orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
				hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
				model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersAccountForm);
			}
		}
		if (hpeOrdersForm.getHpeOrdersShipAddressForm() != null)
		{
			final HPEOrdersShipAddressForm hpeOrdersShipAddressForm = hpeOrdersForm.getHpeOrdersShipAddressForm();
			hpeOrdersForm.setHpeOrdersShipAddressForm(hpeOrdersShipAddressForm);
			model.addAttribute(HPE_ORDERS_SHIPADDRESS_FORM, hpeOrdersShipAddressForm);
			final AddressData hpeShipingAddressData = orderFormDataBindingHelper.createOrdersShipAddressData(model,
					hpeOrdersShipAddressForm, i18NFacade);
			hpeOrdersData.setHpeShipingAddressData(hpeShipingAddressData);

		}

		if (hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
		{
			final HPEOrdersBillAddressForm hpeOrdersBillAddressForm = hpeOrdersForm.getHpeOrdersBillAddressForm();
			hpeOrdersForm.setHpeOrdersBillAddressForm(hpeOrdersBillAddressForm);
			model.addAttribute(HPE_ORDERS_BILLADDRESS_FORM, hpeOrdersBillAddressForm);
			final AddressData hpeBillingAddressData = orderFormDataBindingHelper.createOrdersBillAddressData(model,
					hpeOrdersBillAddressForm, i18NFacade);
			hpeOrdersData.setHpeBillingAddressData(hpeBillingAddressData);
		}

		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);

		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);


		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));

		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Checkout.HpeEditOrdersAccountPage;
	}


	/**
	 * Method to edit the Quote Shipping Address Information.
	 *
	 * @param hpeOrdersForm
	 * @param model
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException
	 */


	@RequestMapping(value = "/editQuoteShippingAddress", method = RequestMethod.GET)
	public String editQuoteShippingAddress(@RequestParam("addressId") final String addressId,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model, final RedirectAttributes redirectModel,
			@ModelAttribute final HPEOrdersData hpeOrdersData) throws CMSItemNotFoundException
	{

		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
			model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersAccountForm);
		}
		if (hpeOrdersForm.getHpeOrdersShipAddressForm() != null)
		{
			final List<SelectOption> formattedAddresses = populateAddresses(hpeUserFacade.getB2BUnitAccountAllAddressesList());
			model.addAttribute("formattedAddresses", formattedAddresses);
			final HPEOrdersShipAddressForm hpeQuoteShipAddressForm = hpeOrdersForm.getHpeOrdersShipAddressForm();
			if (hpeQuoteShipAddressForm.getAddressId().equals(addressId))
			{
				hpeOrdersForm.setHpeOrdersShipAddressForm(hpeQuoteShipAddressForm);
				model.addAttribute(HPE_ORDERS_SHIPADDRESS_FORM, hpeQuoteShipAddressForm);
			}
		}
		if (hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
		{
			final HPEOrdersBillAddressForm hpeOrdersBillAddressForm = hpeOrdersForm.getHpeOrdersBillAddressForm();
			hpeOrdersForm.setHpeOrdersBillAddressForm(hpeOrdersBillAddressForm);
			model.addAttribute(HPE_ORDERS_BILLADDRESS_FORM, hpeOrdersBillAddressForm);
			final AddressData hpeBillingAddressData = orderFormDataBindingHelper.createOrdersBillAddressData(model,
					hpeOrdersBillAddressForm, i18NFacade);
			hpeOrdersData.setHpeBillingAddressData(hpeBillingAddressData);
		}

		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);

		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Checkout.HpeEditOrdersShippingAddressPage;
	}

	/**
	 * Method to edit the Quote Billing Address Information.
	 *
	 * @param hpeOrdersForm
	 * @param model
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/editQuoteBillingAddress", method = RequestMethod.GET)
	public String editQuoteBillingAddress(@RequestParam("addressId") final String addressId,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model, final RedirectAttributes redirectModel,
			@ModelAttribute final HPEOrdersData hpeOrdersData) throws CMSItemNotFoundException
	{
		LOG.info("editQuoteBillingAddress method starts==addressId======>" + addressId);
		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
			model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersAccountForm);
		}
		if (hpeOrdersForm.getHpeOrdersShipAddressForm() != null)
		{
			final HPEOrdersShipAddressForm hpeOrdersShipAddressForm = hpeOrdersForm.getHpeOrdersShipAddressForm();
			hpeOrdersForm.setHpeOrdersShipAddressForm(hpeOrdersShipAddressForm);
			model.addAttribute(HPE_ORDERS_SHIPADDRESS_FORM, hpeOrdersShipAddressForm);
			final AddressData hpeShipingAddressData = orderFormDataBindingHelper.createOrdersShipAddressData(model,
					hpeOrdersShipAddressForm, i18NFacade);
			hpeOrdersData.setHpeShipingAddressData(hpeShipingAddressData);
		}
		if (hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
		{
			final HPEOrdersBillAddressForm hpeOrdersBillAddressForm = hpeOrdersForm.getHpeOrdersBillAddressForm();
			if (hpeOrdersBillAddressForm.getAddressId().equals(addressId))
			{
				hpeOrdersForm.setHpeOrdersBillAddressForm(hpeOrdersBillAddressForm);
				model.addAttribute(HPE_ORDERS_BILLADDRESS_FORM, hpeOrdersBillAddressForm);
			}
		}
		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);

		model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));

		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Checkout.HpeEditOrdersBillingAddressPage;
	}


	@RequireHardLogIn
	@RequestMapping(value = "/saveAcctTransaction", method = RequestMethod.POST)
	public String saveQuoteAccountTransaction(@RequestParam("acctUserUid") final String acctUserUid,
			@ModelAttribute("hpeOrdersForm") final HpeOrdersForm hpeOrdersForm, final Model model,
			@ModelAttribute("hpeOrdersData") final HPEOrdersData hpeOrdersData, final BindingResult bindingResult,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException, IOException
	{
		LOG.info("saveQuoteAccountTransaction method starts==acctUserUid==" + acctUserUid);
		if (bindingResult.hasErrors())
		{
			return "SaveAccountTransactionError";
		}
		//Save Account new values
		final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
		if (hpeOrdersForm.getHpeOrdersAccountForm() != null && hpeOrdersAccountForm.getAccountUidEmail().equals(acctUserUid))
		{
			final HPEOrdersAccountForm hpeOrdersAccountFormUpdate = new HPEOrdersAccountForm();
			hpeOrdersAccountFormUpdate.setFirstName(hpeOrdersAccountForm.getFirstName());
			hpeOrdersAccountFormUpdate.setLastName(hpeOrdersAccountForm.getLastName());
			hpeOrdersAccountFormUpdate.setAccountB2BCompany(hpeOrdersAccountForm.getAccountB2BCompany());
			hpeOrdersAccountFormUpdate.setAccountUidEmail(hpeOrdersAccountForm.getAccountUidEmail());
			if (hpeOrdersAccountForm.getUploadQuoteFilesList() != null)
			{
				final List<MultipartFile> uploadQuoteFilesList = hpeOrdersAccountForm.getUploadQuoteFilesList();
				hpeOrdersAccountFormUpdate.setUploadQuoteFilesList(uploadQuoteFilesList);
				final Iterator<MultipartFile> itrFile = uploadQuoteFilesList.iterator();
				int uploadFileCounter = 0;
				while (itrFile.hasNext())
				{
					final MultipartFile multipartFile = itrFile.next();
					if (multipartFile != null)
					{
						hpeOrdersAccountFormUpdate.setUploadQuoteFile(multipartFile);
						hpeOrdersAccountFormUpdate.setUploadQuoteFileCount(uploadFileCounter++);
					}
				}
			}

			if (hpeOrdersAccountForm.getQuoteMediaModelList() != null)
			{
				final Collection<MediaModel> quoteMediaModelList = hpeOrdersAccountForm.getQuoteMediaModelList();
				hpeOrdersAccountFormUpdate.setQuoteMediaModelList(quoteMediaModelList);
				final Iterator<MediaModel> itrMediaFile = quoteMediaModelList.iterator();
				while (itrMediaFile.hasNext())
				{
					final MediaModel mediaModel = itrMediaFile.next();
					if (mediaModel != null)
					{
						hpeOrdersAccountFormUpdate.setQuoteMediaPK(mediaModel.getPk().toString());
						hpeOrdersAccountFormUpdate.setQuoteMediaURL(hpeOrdersAccountForm.getQuoteMediaURL());
					}
				}
			}


			if (hpeOrdersAccountForm.getMediaFileUrlMap() != null)
			{
				final Map<MultipartFile, String> mediaFileUrlMap = hpeOrdersAccountForm.getMediaFileUrlMap();
				hpeOrdersAccountFormUpdate.setMediaFileUrlMap(mediaFileUrlMap);
				final Set<Entry<MultipartFile, String>> setMap = mediaFileUrlMap.entrySet();
				final Iterator<Entry<MultipartFile, String>> itr = setMap.iterator();
				while (itr.hasNext())
				{
					final Entry<MultipartFile, String> entry = itr.next();
					if (entry != null)
					{
						final MultipartFile multipartFile = entry.getKey();
						final String mediaUrl = entry.getValue();
						hpeOrdersAccountFormUpdate.setUploadQuoteFile(multipartFile);
						hpeOrdersAccountFormUpdate.setQuoteMediaURL(mediaUrl);
					}
				}
			}

			final Map<String, String> custFieldsMap = hpeOrdersAccountForm.getCustomFieldsUnitMap();
			hpeOrdersAccountFormUpdate.setCustomFieldsUnitMap(custFieldsMap);
			final HashMap<String, String> custFieldsQuoteSaveMap = new HashMap<String, String>();
			final List<HPEAccountCustomFieldsData> updatedCustomFieldsFormList = new ArrayList<HPEAccountCustomFieldsData>();
			final List<HPEAccountCustomFieldsData> customFieldsFormList = hpeOrdersAccountForm.getCustomFieldsFormList();
			if (null != customFieldsFormList && !customFieldsFormList.isEmpty())
			{
				int activeCustomFieldsSize = 0;
				for (int index = 0; index < customFieldsFormList.size(); index++)
				{
					if (customFieldsFormList.get(index).getDisplay())
					{
						activeCustomFieldsSize += 1;
					}
				}
				HPEAccountCustomFieldsData custFieldsData = null;
				String[] customFieldValueArray = null;
				LOG.info("No Of Fields in the form ::: " + activeCustomFieldsSize);

				if (null != hpeOrdersAccountForm.getCustomFieldValue())
				{
					customFieldValueArray = hpeOrdersAccountForm.getCustomFieldValue().split(",");
					LOG.info("No Of Values in Array ::: " + customFieldValueArray.length);
				}
				for (int i = 0; i < activeCustomFieldsSize; i++)
				{
					custFieldsData = customFieldsFormList.get(i);
					if (null != customFieldValueArray && customFieldValueArray.length > 0)
					{
						custFieldsData.setCustomValue(customFieldValueArray[i]);
					}
					custFieldsQuoteSaveMap.put(custFieldsData.getCustomLabel(), custFieldsData.getCustomValue());
					updatedCustomFieldsFormList.add(custFieldsData);
				}
				hpeOrdersAccountFormUpdate.setCustomFieldValue(hpeOrdersAccountForm.getCustomFieldValue());
				LOG.info("custFieldsQuoteSaveMap size ::: " + custFieldsQuoteSaveMap.size());
				hpeOrdersAccountFormUpdate.setCustomFieldsQuoteMap(custFieldsQuoteSaveMap);
				hpeOrdersAccountFormUpdate.setCustomFieldsFormList(updatedCustomFieldsFormList);
			}
			else
			{
				LOG.error("CustomFields List is EMPTY !!!");
			}
			orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountFormUpdate, hpeOrdersData);
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountFormUpdate);
			model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersAccountFormUpdate);
		}

		if (hpeOrdersForm.getHpeOrdersShipAddressForm() != null)
		{
			final HPEOrdersShipAddressForm hpeQuoteShipAddressForm = hpeOrdersForm.getHpeOrdersShipAddressForm();
			hpeOrdersForm.setHpeOrdersShipAddressForm(hpeQuoteShipAddressForm);
			model.addAttribute(HPE_ORDERS_SHIPADDRESS_FORM, hpeQuoteShipAddressForm);

			final AddressData hpeShipingAddressData = orderFormDataBindingHelper.createOrdersShipAddressData(model,
					hpeQuoteShipAddressForm, i18NFacade);
			hpeOrdersData.setHpeShipingAddressData(hpeShipingAddressData);
			model.addAttribute(HPE_ORDERS_SHIPPINGADDRESS_DATA, hpeShipingAddressData);

		}
		if (hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
		{
			final HPEOrdersBillAddressForm hpeOrdersBillAddressForm = hpeOrdersForm.getHpeOrdersBillAddressForm();
			hpeOrdersForm.setHpeOrdersBillAddressForm(hpeOrdersBillAddressForm);
			model.addAttribute(HPE_ORDERS_BILLADDRESS_FORM, hpeOrdersBillAddressForm);

			final AddressData hpeBillingAddressData = orderFormDataBindingHelper.createOrdersBillAddressData(model,
					hpeOrdersBillAddressForm, i18NFacade);
			model.addAttribute(HPE_ORDERS_BILLINGADDRESS_DATA, hpeBillingAddressData);
		}
		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);

		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));

		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Quote.HpeQuoteCreateAccountPage;
	}


	/**
	 *
	 * @param type
	 * @param addressId
	 * @param hpeOrdersForm
	 * @param hpeOrdersData
	 * @param model
	 * @param redirectModel
	 * @param bindingResult
	 * @return
	 * @throws CMSItemNotFoundException
	 */

	@RequireHardLogIn
	@RequestMapping(value = "/saveAddrsTransaction", method = RequestMethod.POST)
	public String saveShippingBillingAddressTransaction(@RequestParam("type") final String type,
			@ModelAttribute("hpeOrdersForm") final HpeOrdersForm hpeOrdersForm,
			@ModelAttribute("hpeOrdersData") final HPEOrdersData hpeOrdersData, final Model model,
			final RedirectAttributes redirectModel, final BindingResult bindingResult) throws CMSItemNotFoundException
	{
		LOG.info("saveAddressTransaction method starts==type==" + type);
		if (bindingResult.hasErrors())
		{
			return "SaveAddressTransactionError";
		}
		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
			model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersAccountForm);
		}

		if (StringUtils.equals(type, "ship") && hpeOrdersForm.getHpeOrdersShipAddressForm() != null)
		{
			orderFormDataBindingHelper.saveShipAddressTransaction(model, hpeOrdersForm, hpeOrdersData, i18NFacade);
		}
		else if (StringUtils.equals(type, "bill") && hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
		{
			orderFormDataBindingHelper.saveBillAddressTransaction(model, hpeOrdersForm, hpeOrdersData, i18NFacade);
		}

		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);

		model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);

		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));

		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Quote.HpeQuoteCreateAccountPage;
	}

	/**
	 * @param addressId
	 * @param isShipAddrs
	 * @param countryIsoCode
	 * @param hpeOrdersForm
	 * @param model
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/selectShipAddress", method = RequestMethod.GET)
	public String selectedShippingAddressTransaction(@RequestParam("selectShipAddressId") final String selectShipAddressId,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, @ModelAttribute final HPEOrdersData hpeOrdersData, final Model model,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		LOG.info("selectedShippingAddressTransaction method starts==selectShipAddressId==" + selectShipAddressId);
		LOG.info("selectedShippingAddressTransaction method starts==hpeOrdersForm==" + hpeOrdersForm);

		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
			model.addAttribute(HPE_ORDERS_ACCOUNT_FORM, hpeOrdersAccountForm);
		}

		final List<SelectOption> formattedAddresses = populateAddresses(hpeUserFacade.getB2BUnitAccountAllAddressesList());
		model.addAttribute("formattedAddresses", formattedAddresses);
		final String[] selectAddressID = selectShipAddressId.split(",");

		for (int i = 0; i < selectAddressID.length; i++)
		{
			final String addressID = selectAddressID[i];
			for (final SelectOption selectedOption : formattedAddresses)
			{
				final String formAddId = hpeOrdersForm.getHpeOrdersShipAddressForm().getAddressId();
				if (selectedOption.getCode() != null && selectedOption.getCode().equals(addressID) && formAddId.equals(addressID))
				{
					final AddressData selectAddressObj = hpeUserFacade.getSelectShippingAddresses(selectShipAddressId);
					LOG.info("selectShipAddressId-==ship---" + selectShipAddressId);
					orderFormDataBindingHelper.setOrdersShipAddressForm(model, hpeOrdersForm, selectAddressObj);
				}
			}
		}

		if (hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
		{
			final HPEOrdersBillAddressForm hpeOrdersBillAddressForm = hpeOrdersForm.getHpeOrdersBillAddressForm();
			hpeOrdersForm.setHpeOrdersBillAddressForm(hpeOrdersBillAddressForm);
			model.addAttribute(HPE_ORDERS_BILLADDRESS_FORM, hpeOrdersBillAddressForm);

			final AddressData hpeBillingAddressData = orderFormDataBindingHelper.createOrdersBillAddressData(model,
					hpeOrdersBillAddressForm, i18NFacade);
			hpeOrdersData.setHpeBillingAddressData(hpeBillingAddressData);
			model.addAttribute(HPE_ORDERS_BILLINGADDRESS_DATA, hpeBillingAddressData);
		}
		model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);

		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));

		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);

		LOG.info("selectedShippingAddressTransaction method is ends-----HpeEditQuoteShippingAddressPage---->");
		return ControllerConstants.Views.Pages.Checkout.HpeEditOrdersShippingAddressPage;

	}


	/**
	 * Set the CartData entries in the model object.
	 *
	 * @author shaaiee
	 * @param hpeOrdersForm
	 * @param model
	 * @throws CMSItemNotFoundException
	 */
	private CartData setQuoteCartDataInfoView(final String cartCode, final HpeOrdersForm hpeOrdersForm,
			final HPEOrdersData hpeOrdersData, final Model model) throws CommerceSaveCartException, CMSItemNotFoundException
	{
		final CartData hpeCartData;
		if (saveCartFacade.isSessionCart(cartCode))
		{
			hpeCartData = getCartFacade().getSessionCartWithEntryOrdering(false);
		}
		else
		{
			final CommerceSaveCartParameterData parameter = new CommerceSaveCartParameterData();
			parameter.setCartId(cartCode);
			final CommerceSaveCartResultData resultData = saveCartFacade.getCartForCodeAndCurrentUser(parameter);
			hpeCartData = resultData.getSavedCartData();
		}
		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);

		hpeOrdersData.setHpeCartData(hpeCartData);
		hpeOrdersForm.setHpeCartData(hpeCartData);
		model.addAttribute(HPE_CARTDATA, hpeCartData);

		return hpeCartData;
	}

	protected void prepareDataForPage(final HpeOrdersForm hpeOrdersForm, final HPEOrdersData hpeOrdersData, final Model model)
			throws CMSItemNotFoundException
	{
		super.prepareDataForPage(model);
		final UpdateQuantityForm updateQuantityForm = new UpdateQuantityForm();
		updateQuantityForm.setQuantity(0L);
		model.addAttribute("updateQuantityForm", updateQuantityForm);
	}

	private void setQuoteformCartDataInfo(final HpeOrdersForm hpeOrdersForm, final HPEOrdersData hpeOrdersData, final Model model)
			throws CMSItemNotFoundException
	{
		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);
	}

	private void setQuoteExpiryDataInfo(final HpeOrdersForm hpeOrdersForm, final HPEOrdersData hpeOrdersData, final Model model)
			throws CMSItemNotFoundException
	{
		final Locale currentLocale = getI18nService().getCurrentLocale();
		final String expirationTimePattern = getMessageSource().getMessage(DATE_FORMAT_KEY, null, currentLocale);
		final Date expiryDate = hpeUserFacade.setHpeQuoteExpirationTime();
		LOG.info("setQuoteExpiryDataInfo======expiryDate==" + expiryDate);
		hpeOrdersForm.setExpirationTime(
				QuoteExpirationTimeConverter.convertDateToString(expiryDate, expirationTimePattern, currentLocale));
		hpeOrdersData.setExpirationTime(expiryDate);
	}

	/**
	 * Convert the Customer Account Form HPEAccountQuoteForm into the HPEQuoteData
	 *
	 * @param hpeOrdersForm
	 * @param cartData
	 */

	private HPEOrdersAccountData createHpeAccountDataFromHpeForm(final Model model, final HPEOrdersData hpeOrdersData,
			final HPEOrdersAccountForm hpeOrdersAccountForm)
	{
		final HPEOrdersAccountData hpeAccountQuoteData = new HPEOrdersAccountData();
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getFirstName()))
		{
			hpeAccountQuoteData.setFirstName(hpeOrdersAccountForm.getFirstName());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getLastName()))
		{
			hpeAccountQuoteData.setLastName(hpeOrdersAccountForm.getLastName());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getEmail()))
		{
			hpeAccountQuoteData.setEmail(hpeOrdersAccountForm.getEmail());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getPhone()))
		{
			hpeAccountQuoteData.setPhone(hpeOrdersAccountForm.getPhone());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getCompany()))
		{
			hpeAccountQuoteData.setCompany(hpeOrdersAccountForm.getCompany());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getAccountB2BCompany()))
		{
			hpeAccountQuoteData.setAccountB2BCompany(hpeOrdersAccountForm.getAccountB2BCompany());
		}
		if (StringUtils.isNotEmpty(hpeOrdersAccountForm.getAccountUidEmail()))
		{
			hpeAccountQuoteData.setAccountUidEmail(hpeOrdersAccountForm.getAccountUidEmail());
		}
		hpeAccountQuoteData.setUploadOrdersFile(null);
		hpeAccountQuoteData.setUploadOrdersFilesList(null);
		hpeAccountQuoteData.setOrdersMediaModelList(null);
		if (hpeOrdersAccountForm.getCustomFieldsFormList() != null && hpeOrdersAccountForm.getCustomFieldsFormList().size() > 0)
		{
			hpeAccountQuoteData.setHpeAcctCustomFieldsData(hpeOrdersAccountForm.getCustomFieldsFormList());
		}
		hpeOrdersData.setHpeOrdersAccountData(hpeAccountQuoteData);
		model.addAttribute(HPE_ACCOUNTQUOTE_DATA, hpeAccountQuoteData);
		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		return hpeAccountQuoteData;
	}

	/**
	 * Set the QuoteOrder is Delete status
	 *
	 * @param model
	 * @param redirectModel
	 * @param quoteCode
	 * @param hpeOrdersForm
	 * @param hpeOrdersData
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@ResponseBody
	@RequestMapping(value = "/delQuoteSummary", method = RequestMethod.GET)
	@RequireHardLogIn
	private String deleteHpeQuoteAction(final Model model, final RedirectAttributes redirectModel,
			@RequestParam("quoteCode") final String quoteCode, final HpeOrdersForm hpeOrdersForm, final HPEOrdersData hpeOrdersData)
			throws CMSItemNotFoundException
	{
		String response = "success";
		try
		{
			LOG.info("DeleteHpeQuoteAction======quoteCode==" + quoteCode);
			hpeDefaultQuoteFacade.deleteHpeQuote(quoteCode);
			storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
			setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		}
		catch (final Exception e)
		{
			LOG.error("QuoteSummary - Hide the Quote Exception occured !!!, the exception is " + e.getMessage());
			response = "failure";
		}
		GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "text.quote.order.delete.status",
				new Object[]
				{ quoteCode });
		return response;

	}

	/***
	 * Cancel the Edit Quote Transaction Page
	 *
	 * @param hpeOrdersForm
	 * @param model
	 * @param hpeOrdersData
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequestMapping(value = "/cancelQuoteView", method = RequestMethod.GET)
	@RequireHardLogIn
	public String cancelHpeQuoteView(@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model,
			@ModelAttribute final HPEOrdersData hpeOrdersData) throws CMSItemNotFoundException
	{

		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
		}
		if (hpeOrdersForm.getHpeOrdersShipAddressForm() != null)
		{
			final HPEOrdersShipAddressForm hpeQuoteShipAddressForm = hpeOrdersForm.getHpeOrdersShipAddressForm();
			hpeOrdersForm.setHpeOrdersShipAddressForm(hpeQuoteShipAddressForm);
			final AddressData hpeShipingAddressData = orderFormDataBindingHelper.createOrdersShipAddressData(model,
					hpeQuoteShipAddressForm, i18NFacade);
			hpeOrdersData.setHpeShipingAddressData(hpeShipingAddressData);
		}

		if (hpeOrdersForm.getHpeOrdersBillAddressForm() != null)
		{
			final HPEOrdersBillAddressForm hpeQuoteBillAddressForm = hpeOrdersForm.getHpeOrdersBillAddressForm();
			hpeOrdersForm.setHpeOrdersBillAddressForm(hpeQuoteBillAddressForm);
			final AddressData hpeBillingAddressData = orderFormDataBindingHelper.createOrdersBillAddressData(model,
					hpeQuoteBillAddressForm, i18NFacade);
			hpeOrdersData.setHpeBillingAddressData(hpeBillingAddressData);
		}

		prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);

		storeCmsPageInModel(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(QUOTE_CREATE_HPE_QUOTE_CMS_PAGE));
		model.addAttribute(WebConstants.BREADCRUMBS_KEY, resourceBreadcrumbBuilder.getBreadcrumbs(BREADCRUMB_QUOTE_CREATE));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Quote.HpeQuoteCreateAccountPage;

	}

	private List<AddressData> getB2BUnitAddressList(final Model model)
	{

		final List<AddressData> alladdressDataList = hpeUserFacade.getB2BUnitAccountAllAddressesList();
		model.addAttribute("b2bUnitAddressList", alladdressDataList);
		return alladdressDataList;
	}

	private static class SelectOption
	{
		private final String code;
		private final String name;

		public SelectOption(final String code, final String name)
		{
			this.code = code;
			this.name = name;
		}

		public String getCode()
		{
			return code;
		}

		public String getName()
		{
			return name;
		}
	}

	/**
	 * prepares the drop-down options for delivery/ shipping addresses.
	 *
	 * @param allDeliveryAddresses
	 * @return addresses
	 */
	private List<SelectOption> populateAddresses(final List<AddressData> allDeliveryAddresses)
	{
		final List<SelectOption> addresses = new ArrayList<SelectOption>();
		for (final AddressData addressData : allDeliveryAddresses)
		{
			addresses.add(new SelectOption(addressData.getId(), addressData.getFormattedAddress()));
		}
		return addresses;
	}




	/***
	 *
	 * @param model
	 * @param hpeQuoteSendEmailForm
	 * @param hpeQuoteSendEmailFormData
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException6
	 */
	@RequestMapping(value = "/" + CART_CODE_PATH_VARIABLE_PATTERN + "/initSendQuoteEmail", method = RequestMethod.GET)
	public String initSendQuoteEmail(final Model model, final RedirectAttributes redirectModel,
			@PathVariable("cartCode") final String cartCode, @RequestParam("quoteCode") final String quoteCode,
			@Valid final HPEQuoteSendEmailForm hpeQuoteSendEmailForm) throws CMSItemNotFoundException
	{
		LOG.info("initSendQuoteEmail method starts");
		model.addAttribute(new HPEQuoteSendEmailForm());
		hpeQuoteSendEmailForm.setQuoteCode(quoteCode);
		model.addAttribute("hpeQuoteSendEmailForm", hpeQuoteSendEmailForm);
		storeCmsPageInModel(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));
		//return getViewForPage(model);
		return ControllerConstants.Views.Pages.Quote.HpeSendQuoteEmailPage;
	}

	/***
	 *
	 * @param model
	 * @param hpeQuoteSendEmailForm
	 * @param hpeQuoteSendEmailFormData
	 * @param redirectModel
	 * @param bindingResult
	 * @param sessionStatus
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/sendQuoteEmail", method =
	{ RequestMethod.POST, RequestMethod.GET })
	@ResponseBody
	public String sendQuoteEmail(final Model model, @Valid final HPEQuoteSendEmailForm hpeQuoteSendEmailForm,
			@ModelAttribute final HPEQuoteSendEmailFormData hpeQuoteSendEmailFormData, final BindingResult bindingResult,
			final RedirectAttributes redirectModel, final HttpServletRequest request) throws CMSItemNotFoundException
	{
		LOG.info("sendQuoteEmail method starts");
		String response = "success";
		model.addAttribute(new HPEQuoteSendEmailFormData());
		final String quoteCode = hpeQuoteSendEmailForm.getQuoteCode();

		hpeDefaultSendQuoteEmailFormValidator.validate(hpeQuoteSendEmailForm, bindingResult);
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, error.getCode());
			}
			redirectModel.addFlashAttribute("hpeQuoteSendEmailForm", hpeQuoteSendEmailForm);
			return REDIRECT_SENDQUOTE_EMAIL_VIEW_URL;
		}

		final String[] emailids = hpeQuoteSendEmailForm.getHpeEmailIds().split(",");
		final List<String> hpeQuoteEmails = Arrays.asList(emailids);
		hpeQuoteSendEmailFormData.setHpeSubjectLine(hpeQuoteSendEmailForm.getHpeSubjectLine());
		hpeQuoteSendEmailFormData.setHpeEmailIds(hpeQuoteSendEmailForm.getHpeEmailIds());
		hpeQuoteSendEmailFormData.setHpeComments(hpeQuoteSendEmailForm.getHpeComments());
		hpeQuoteSendEmailFormData.setHpeEmailIdsList(hpeQuoteEmails);

		model.addAttribute("hpeQuoteSendEmailForm", hpeQuoteSendEmailForm);
		model.addAttribute("hpeQuoteSendEmailFormData", hpeQuoteSendEmailFormData);

		try
		{
			hpeDefaultQuoteFacade.sendQuoteAsEmail(hpeQuoteSendEmailFormData, quoteCode);
		}
		catch (final Exception e)
		{
			// XXX Auto-generated catch block
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"Error occurred while SendQuote to mail ", null);
			LOG.error(" SendQuote Email Exception occured !!!, the exception is " + e.getMessage());
			response = "failure";
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(HPE_SENDQUOTE_VIA_EMAIL_CMS_PAGE));

		GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "text.quote.sendemail.success",
				new Object[]
				{ quoteCode });

		LOG.info("sendQuoteEmail method ends");
		return REDIRECT_HPE_SENDQUOTE_EMAIL_URL;
		//return REDIRECT_PREFIX + "/sendQuoteEmail";
	}


	protected void fillQuoteForm(final Model model, final AbstractOrderData data)
	{
		if (!model.containsAttribute("quoteForm"))
		{
			final Locale currentLocale = getI18nService().getCurrentLocale();
			final String expirationTimePattern = getMessageSource().getMessage(DATE_FORMAT_KEY, null, currentLocale);

			final QuoteForm quoteForm = new QuoteForm();
			quoteForm.setName(data.getName());
			quoteForm.setDescription(data.getDescription());
			quoteForm.setExpirationTime(
					QuoteExpirationTimeConverter.convertDateToString(data.getExpirationTime(), expirationTimePattern, currentLocale));
			model.addAttribute("quoteForm", quoteForm);
		}
		model.addAttribute("quoteDiscountForm", new QuoteDiscountForm());
	}

	protected void fillVouchers(final Model model)
	{
		model.addAttribute("appliedVouchers", getVoucherFacade().getVouchersForCart());
		if (!model.containsAttribute(VOUCHER_FORM))
		{
			model.addAttribute(VOUCHER_FORM, new VoucherForm());
		}
	}

	protected void setUpdatable(final Model model, final CartData cartData, final boolean updatable)
	{
		for (final OrderEntryData entry : cartData.getEntries())
		{
			entry.setUpdateable(updatable);
		}

		model.addAttribute("disableUpdate", Boolean.valueOf(!updatable));
	}

	protected void setExpirationTimeAttributes(final Model model)
	{
		model.addAttribute("defaultOfferValidityPeriodDays",
				Integer.valueOf(QuoteExpirationTimeUtils.getDefaultOfferValidityPeriodDays()));
		model.addAttribute("minOfferValidityPeriodDays",
				Integer.valueOf(QuoteExpirationTimeUtils.getMinOfferValidityPeriodInDays()));
	}

	protected void prepareQuotePageElements(final Model model, final CartData cartData, final boolean updatable)
	{
		fillQuoteForm(model, cartData);
		fillVouchers(model);
		setUpdatable(model, cartData, updatable);
		loadCommentsShown(model);

		model.addAttribute("savedCartCount", saveCartFacade.getSavedCartsCountForCurrentUser());
		model.addAttribute("quoteCount", quoteFacade.getQuotesCountForCurrentUser());

		setExpirationTimeAttributes(model);
	}

	/**
	 * Adds discount to an existing quote.
	 *
	 * @param quoteCode
	 *           Quote to have discounts applied.
	 * @param form
	 *           Discount info.
	 * @param redirectModel
	 * @return Mapping redirect to quote page.
	 */
	@RequestMapping(value = "{quoteCode}/discount/apply", method = RequestMethod.POST)
	@RequireHardLogIn
	public String applyDiscountAction(@PathVariable("quoteCode") final String quoteCode, @Valid final QuoteDiscountForm form,
			final RedirectAttributes redirectModel)
	{
		try
		{
			getQuoteFacade().applyQuoteDiscount(form.getDiscountRate(), form.getDiscountType());
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error(String.format("Error applying discount for quote %s", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"text.quote.discount.apply.argument.invalid.error", null);
		}
		catch (final SystemException e)
		{
			LOG.error(String.format("Failed to calculate session cart when applying the discount for quote %s", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"text.quote.discount.apply.calculation.error", null);
			return REDIRECT_CART_URL;
		}

		return String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteCode));
	}

	/**
	 * Removes all coupons from the client cart. To be updated in a future release.
	 *
	 * @param redirectModel
	 */
	protected void removeCoupons(final RedirectAttributes redirectModel)
	{
		final List<VoucherData> vouchers = voucherFacade.getVouchersForCart();

		for (final VoucherData voucher : vouchers)
		{
			try
			{
				voucherFacade.releaseVoucher(voucher.getCode());
			}
			catch (final VoucherOperationException e)
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "text.voucher.release.error",
						new Object[]
						{ voucher.getCode() });
				if (LOG.isDebugEnabled())
				{
					LOG.debug(e.getMessage(), e);
				}
			}
		}
	}

	@RequestMapping(value = "/{quoteCode}/cancel", method = RequestMethod.POST)
	@RequireHardLogIn
	public String cancelQuote(@PathVariable("quoteCode") final String quoteCode, final RedirectAttributes redirectModel)
	{
		try
		{
			quoteFacade.cancelQuote(quoteCode);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, QUOTE_TEXT_CANCEL_SUCCESS,
					new Object[]
					{ quoteCode });

		}
		catch (final UnknownIdentifierException uie)
		{
			LOG.warn("Attempted to cancel a quote that does not exist or is not visible: " + quoteCode, uie);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_TEXT_NOT_CANCELABLE,
					new Object[]
					{ quoteCode });
		}
		catch (final CommerceQuoteAssignmentException e)
		{
			LOG.warn("Attempted to cancel a quote that is assigned to another user: " + quoteCode, e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.INFO_MESSAGES_HOLDER, QUOTE_EDIT_LOCKED_ERROR, new Object[]
			{ quoteCode, e.getAssignedUser() });
			return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
		}

		return REDIRECT_QUOTE_LIST_URL;
	}

	/**
	 * Submit quote to next responsible in the workflow (e.g. from buyer to seller, from sales representative to sales
	 * approver).
	 *
	 * @param quoteCode
	 * @param redirectModel
	 * @return Mapping of redirect to the quote details page.
	 */
	@RequestMapping(value = "/{quoteCode}/submit", method = RequestMethod.POST)
	@RequireHardLogIn
	public String submitQuote(@PathVariable("quoteCode") final String quoteCode,
			@RequestParam(value = "editMode", defaultValue = "false") final boolean editMode, final QuoteForm quoteForm,
			final BindingResult bindingResult, final RedirectAttributes redirectModel)
	{
		if (validateCart(redirectModel))
		{
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_SUBMIT_ERROR, null);
			return String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteCode));
		}

		try
		{
			if (editMode)
			{
				final Optional<String> optionalUrl = handleEditModeSubmitQuote(quoteCode, quoteForm, bindingResult, redirectModel);
				if (optionalUrl.isPresent())
				{
					return optionalUrl.get();
				}
			}
			removeCoupons(redirectModel);
			getQuoteFacade().submitQuote(quoteCode);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, QUOTE_SUBMIT_SUCCESS, null);
		}
		catch (final CommerceQuoteAssignmentException cqae)
		{
			LOG.warn("Attempted to submit a quote that is assigned to another user: " + quoteCode, cqae);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.INFO_MESSAGES_HOLDER, QUOTE_EDIT_LOCKED_ERROR, new Object[]
			{ quoteCode, cqae.getAssignedUser() });
			return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
		}
		catch (final IllegalQuoteSubmitException e)
		{
			LOG.warn("Attempt to submit a quote that is not allowed.", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_NOT_SUBMITABLE_ERROR);
			return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
		}
		catch (final QuoteUnderThresholdException e)
		{
			final double quoteThreshold = getQuoteFacade().getQuoteRequestThreshold(quoteCode);
			LOG.error(String.format("Quote %s under threshold", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_REJECT_INITIATION_REQUEST,
					new Object[]
					{ getFormattedPriceValue(quoteThreshold) });
			return String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteCode));
		}
		catch (final IllegalStateException | UnknownIdentifierException | ModelSavingException | IllegalArgumentException e)
		{
			LOG.error(String.format("Unable to submit quote %s", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_SUBMIT_ERROR, null);
			return REDIRECT_PREFIX + ROOT;
		}
		return REDIRECT_QUOTE_LIST_URL;
	}

	/**
	 * Approve a quote from the sales representative
	 *
	 * @param quoteCode
	 * @param redirectModel
	 * @return Mapping of redirect to the quote details page.
	 */
	@RequestMapping(value = "/{quoteCode}/approve", method = RequestMethod.POST)
	@RequireHardLogIn
	public String approveQuote(@PathVariable("quoteCode") final String quoteCode, final RedirectAttributes redirectModel)
	{
		try
		{
			getQuoteFacade().approveQuote(quoteCode);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "quote.approve.success", null);
		}
		catch (final IllegalStateException | UnknownIdentifierException | ModelSavingException | IllegalArgumentException e)
		{
			LOG.error(String.format("Unable to approve quote %s", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "quote.approve.error", null);
			return REDIRECT_PREFIX + ROOT;
		}
		return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
	}

	/**
	 * Reject a quote from the sales representative
	 *
	 * @param quoteCode
	 * @param redirectModel
	 * @return Mapping of redirect to the quote details page.
	 */
	@RequestMapping(value = "/{quoteCode}/reject", method = RequestMethod.POST)
	@RequireHardLogIn
	public String rejectQuote(@PathVariable("quoteCode") final String quoteCode, final RedirectAttributes redirectModel)
	{
		try
		{
			getQuoteFacade().rejectQuote(quoteCode);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, "quote.reject.success", null);
		}
		catch (final IllegalStateException | UnknownIdentifierException | ModelSavingException | IllegalArgumentException e)
		{
			LOG.error(String.format("Unable to reject quote %s", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "quote.reject.error", null);
			return REDIRECT_PREFIX + ROOT;
		}

		return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
	}

	@RequestMapping(value = "/{quoteCode}/requote", method = RequestMethod.POST)
	@RequireHardLogIn
	public String requote(@PathVariable("quoteCode") final String quoteCode, final RedirectAttributes redirectModel)
	{

		try
		{
			removeCoupons(redirectModel);
			final QuoteData quoteData = getQuoteFacade().requote(quoteCode);

			return String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteData.getCode()));
		}
		catch (final IllegalQuoteStateException | CannotCloneException | IllegalArgumentException | ModelSavingException e)
		{
			LOG.error("Unable to requote", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_REQUOTE_ERROR, null);
			return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
		}
	}

	protected Optional<String> handleEditModeSubmitQuote(final String quoteCode, final QuoteForm quoteForm,
			final BindingResult bindingResult, final RedirectAttributes redirectModel)
	{
		final boolean isSeller = Functions.isQuoteUserSalesRep();
		final Object validationGroup = isSeller ? QuoteForm.Seller.class : QuoteForm.Buyer.class;

		smartValidator.validate(quoteForm, bindingResult, validationGroup);

		if (bindingResult.hasErrors())
		{
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					isSeller ? "text.quote.expiration.time.invalid" : "text.quote.name.description.invalid", null);
			return Optional.of(String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteCode)));
		}

		try
		{
			CommerceCartMetadata cartMetadata;
			if (isSeller)
			{
				final Optional<Date> expirationTime = Optional.ofNullable(getExpirationDateFromString(quoteForm.getExpirationTime()));
				cartMetadata = CommerceCartMetadataUtils.metadataBuilder().expirationTime(expirationTime)
						.removeExpirationTime(!expirationTime.isPresent()).build();
			}
			else
			{
				cartMetadata = CommerceCartMetadataUtils.metadataBuilder().name(Optional.ofNullable(quoteForm.getName()))
						.description(Optional.ofNullable(quoteForm.getDescription())).build();
			}

			getCartFacade().updateCartMetadata(cartMetadata);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.warn(String.format("Invalid metadata input field(s) for quote %s", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					isSeller ? "text.quote.expiration.time.invalid" : "text.quote.name.description.invalid", null);
			return Optional.of(String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteCode)));
		}

		return Optional.empty();
	}

	@RequestMapping(value = "/{quoteCode}/newcart", method = RequestMethod.GET)
	@RequireHardLogIn
	public String newCart(@PathVariable("quoteCode") final String quoteCode, final RedirectAttributes redirectModel)
			throws CMSItemNotFoundException
	{
		try
		{
			removeCoupons(redirectModel);
			final QuoteData quoteData = quoteFacade.newCart();
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, QUOTE_NEWCART_SUCCESS, new Object[]
			{ quoteData.getCode() });
			return REDIRECT_CART_URL;
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error("Unable to sync cart into quote. Illegal argument used to invoke a method", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_NEWCART_ERROR, null);
			return String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteCode));
		}
		catch (final SystemException e)
		{
			LOG.error("Unable to save quote while trying to close quote edit mode", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_NEWCART_ERROR, null);
			return String.format(REDIRECT_QUOTE_EDIT_URL, urlEncode(quoteCode));
		}
	}

	/**
	 * Place an order for the given quote.
	 *
	 * @param quoteCode
	 * @param redirectModel
	 * @return Mapping of redirect to the checkout page.
	 */
	@RequestMapping(value = "/{quoteCode}/checkout", method = RequestMethod.POST)
	@RequireHardLogIn
	public String placeOrder(@PathVariable("quoteCode") final String quoteCode, final RedirectAttributes redirectModel)
	{
		try
		{
			getQuoteFacade().acceptAndPrepareCheckout(quoteCode);
		}
		catch (final CommerceQuoteExpirationTimeException e)
		{
			LOG.warn(String.format("Quote has Expired. Quote Code : [%s]", quoteCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_EXPIRED, null);
			return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
		}
		catch (final UnknownIdentifierException e)
		{
			LOG.warn(String.format("Attempted to place order with a quote that does not exist or is not visible: %s", quoteCode), e);
			return REDIRECT_QUOTE_LIST_URL;
		}
		catch (final SystemException e)
		{
			LOG.warn("There was error saving the session cart", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, QUOTE_SAVE_CART_ERROR, null);
			return String.format(REDIRECT_QUOTE_DETAILS_URL, urlEncode(quoteCode));
		}
		return getCheckoutRedirectUrl();
	}

	@ResponseBody
	@RequestMapping(value = "/{quoteCode}/expiration", method = RequestMethod.POST)
	@RequireHardLogIn
	public ResponseEntity<String> setQuoteExpiration(@PathVariable("quoteCode") final String quoteCode, final QuoteForm quoteForm,
			final BindingResult bindingResult)
	{
		smartValidator.validate(quoteForm, bindingResult, QuoteForm.Seller.class);

		if (bindingResult.hasErrors())
		{
			final String errorMessage = getMessageSource().getMessage(bindingResult.getAllErrors().get(0).getCode(), null,
					getI18nService().getCurrentLocale());
			return new ResponseEntity<String>(errorMessage, HttpStatus.BAD_REQUEST);
		}

		try
		{
			final Optional<Date> expirationTime = Optional.ofNullable(getExpirationDateFromString(quoteForm.getExpirationTime()));
			final CommerceCartMetadata cartMetadata = CommerceCartMetadataUtils.metadataBuilder().expirationTime(expirationTime)
					.removeExpirationTime(!expirationTime.isPresent()).build();

			getCartFacade().updateCartMetadata(cartMetadata);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.warn(String.format("Invalid expiration time input for quote %s", quoteCode), e);
			return new ResponseEntity<String>(HttpStatus.BAD_REQUEST);
		}
		catch (final IllegalStateException | IllegalQuoteStateException | UnknownIdentifierException | ModelSavingException e)
		{
			LOG.error(String.format("Failed to update expiration time for quote %s", quoteCode), e);
			return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return new ResponseEntity<String>(HttpStatus.OK);
	}

	/**
	 * Update quote name and description
	 *
	 * @param quoteCode
	 * @param quoteForm
	 * @param bindingResult
	 * @return response entity
	 */
	@ResponseBody
	@RequestMapping(value = "/{quoteCode}/metadata", method = RequestMethod.POST)
	@RequireHardLogIn
	public ResponseEntity<String> setQuoteMetadata(@PathVariable("quoteCode") final String quoteCode, final QuoteForm quoteForm,
			final BindingResult bindingResult)
	{
		smartValidator.validate(quoteForm, bindingResult, QuoteForm.Buyer.class);

		if (bindingResult.hasErrors())
		{
			final String errorMessage = getMessageSource().getMessage(bindingResult.getAllErrors().get(0).getCode(), null,
					getI18nService().getCurrentLocale());
			return new ResponseEntity<>(errorMessage, HttpStatus.BAD_REQUEST);
		}

		try
		{
			final Optional<String> quoteName = Optional.ofNullable(quoteForm.getName());
			final Optional<String> quoteDescription = Optional.ofNullable(quoteForm.getDescription());

			final CommerceCartMetadata cartMetadata = CommerceCartMetadataUtils.metadataBuilder().name(quoteName)
					.description(quoteDescription).build();

			getCartFacade().updateCartMetadata(cartMetadata);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.warn(String.format("Invalid metadata input for quote %s", quoteCode), e);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		catch (final IllegalStateException | UnknownIdentifierException | ModelSavingException e)
		{
			LOG.error(String.format("Failed to update metadata for quote %s", quoteCode), e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	protected Date getExpirationDateFromString(final String expirationTime)
	{
		final Locale currentLocale = getI18nService().getCurrentLocale();
		final String expirationTimePattern = getMessageSource().getMessage(DATE_FORMAT_KEY, null, currentLocale);

		return QuoteExpirationTimeConverter.convertStringToDate(expirationTime, expirationTimePattern, currentLocale);
	}

	/**
	 * Add a quote comment to a given quote.
	 *
	 * @param comment
	 * @param redirectModel
	 * @return Mapping of redirect to the quote details page.
	 */
	@RequestMapping(value = "/comment", method = RequestMethod.POST)
	@RequireHardLogIn
	public ResponseEntity<String> addQuoteComment(@RequestParam("comment") final String comment,
			final RedirectAttributes redirectModel)
	{
		try
		{
			getQuoteFacade().addComment(comment);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER,
					"text.confirmation.quote.comment.added");
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error("Attempted to add a comment that is invalid", e);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestMapping(value = "/entry/comment", method = RequestMethod.POST)
	@RequireHardLogIn
	public ResponseEntity<String> addQuoteEntryComment(@RequestParam("entryNumber") final long entryNumber,
			@RequestParam("comment") final String comment, final RedirectAttributes redirectModel)
	{
		try
		{
			getQuoteFacade().addEntryComment(entryNumber, comment);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER,
					"text.confirmation.quote.comment.added");
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error("Attempted to add an entry comment that is invalid", e);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	protected void sortComments(final CartData cartData)
	{
		if (cartData != null)
		{
			if (CollectionUtils.isNotEmpty(cartData.getComments()))
			{
				final List<CommentData> sortedComments = cartData.getComments().stream()
						.sorted((comment1, comment2) -> comment2.getCreationDate().compareTo(comment1.getCreationDate()))
						.collect(Collectors.toList());
				cartData.setComments(sortedComments);
			}

			if (CollectionUtils.isNotEmpty(cartData.getEntries()))
			{
				for (final OrderEntryData orderEntry : cartData.getEntries())
				{
					if (CollectionUtils.isNotEmpty(orderEntry.getComments()))
					{
						final List<CommentData> sortedEntryComments = orderEntry.getComments().stream()
								.sorted((comment1, comment2) -> comment2.getCreationDate().compareTo(comment1.getCreationDate()))
								.collect(Collectors.toList());

						orderEntry.setComments(sortedEntryComments);
					}
					else if (orderEntry.getProduct() != null && orderEntry.getProduct().getMultidimensional() != null
							&& Boolean.TRUE.equals(orderEntry.getProduct().getMultidimensional()))
					{
						if (CollectionUtils.isNotEmpty(orderEntry.getEntries()))
						{
							for (final OrderEntryData multiDOrderEntry : orderEntry.getEntries())
							{
								if (CollectionUtils.isNotEmpty(multiDOrderEntry.getComments()))
								{
									final List<CommentData> sortedMultiDOrderEntryComments = multiDOrderEntry.getComments().stream()
											.sorted((comment1, comment2) -> comment2.getCreationDate().compareTo(comment1.getCreationDate()))
											.collect(Collectors.toList());

									multiDOrderEntry.setComments(sortedMultiDOrderEntryComments);
								}
							}
						}
					}
				}
			}
		}
	}

	protected void loadCommentsShown(final Model model)
	{
		final int commentsShown = getSiteConfigService().getInt(PAGINATION_NUMBER_OF_COMMENTS, 5);
		model.addAttribute("commentsShown", Integer.valueOf(commentsShown));
	}

	/**
	 * Set allowed actions for a given quote on model.
	 *
	 * @param model
	 *           the MVC model
	 * @param quoteCode
	 *           the quote to be checked.
	 */
	protected void setAllowedActions(final Model model, final String quoteCode)
	{
		final Set<QuoteAction> actionSet = getQuoteFacade().getAllowedActions(quoteCode);

		if (actionSet != null)
		{
			final Map<String, Boolean> actionsMap = actionSet.stream()
					.collect(Collectors.toMap((v) -> v.getCode(), (v) -> Boolean.TRUE));
			model.addAttribute(ALLOWED_ACTIONS, actionsMap);
		}
	}

	@ExceptionHandler(IllegalQuoteStateException.class)
	public String handleIllegalQuoteStateException(final IllegalQuoteStateException exception, final HttpServletRequest request)
	{
		final Map<String, Object> currentFlashScope = RequestContextUtils.getOutputFlashMap(request);

		LOG.warn("Invalid quote state for performed action.", exception);

		final String statusMessageKey = String.format("text.account.quote.status.display.%s", exception.getQuoteState());
		final String actionMessageKey = String.format("text.account.quote.action.display.%s", exception.getQuoteAction());

		GlobalMessages.addFlashMessage(currentFlashScope, GlobalMessages.ERROR_MESSAGES_HOLDER, "text.quote.illegal.state.error",
				new Object[]
				{ getMessageSource().getMessage(actionMessageKey, null, getI18nService().getCurrentLocale()), exception.getQuoteCode(), getMessageSource().getMessage(statusMessageKey, null, getI18nService().getCurrentLocale()) });

		return REDIRECT_QUOTE_LIST_URL;
	}

	/**
	 * Get formatted monetary value with currency symbol
	 *
	 * @param value
	 *           the value to be formatted
	 *
	 * @return formatted threshold string
	 */
	protected String getFormattedPriceValue(final double value)
	{
		return priceDataFactory.create(PriceDataType.BUY, BigDecimal.valueOf(value), getCurrentCurrency().getIsocode())
				.getFormattedValue();
	}

	protected ResourceBreadcrumbBuilder getResourceBreadcrumbBuilder()
	{
		return resourceBreadcrumbBuilder;
	}

	protected QuoteFacade getQuoteFacade()
	{
		return quoteFacade;
	}

	protected VoucherFacade getVoucherFacade()
	{
		return voucherFacade;
	}

	/**
	 * @return the b2bUserFacade
	 */
	public B2BUserFacade getB2bUserFacade()
	{
		return b2bUserFacade;
	}

	/**
	 * @return the userService
	 */
	public UserService getUserService()
	{
		return userService;
	}

	/**
	 * @param userService
	 *           the userService to set
	 */
	public void setUserService(final UserService userService)
	{
		this.userService = userService;
	}

	/**
	 * @return the i18NFacade
	 */
	public I18NFacade getI18NFacade()
	{
		return i18NFacade;
	}

	/**
	 * @param i18nFacade
	 *           the i18NFacade to set
	 */
	public void setI18NFacade(final I18NFacade i18nFacade)
	{
		i18NFacade = i18nFacade;
	}

	public HPEDefaultQuoteFacade getHpeDefaultQuoteFacade()
	{
		return hpeDefaultQuoteFacade;
	}

	public void setHpeDefaultQuoteFacade(final HPEDefaultQuoteFacade hpeDefaultQuoteFacade)
	{
		this.hpeDefaultQuoteFacade = hpeDefaultQuoteFacade;
	}

	public CommonsMultipartResolver getMultipartResolver()
	{
		return multipartResolver;
	}

	public void setMultipartResolver(final CommonsMultipartResolver multipartResolver)
	{
		this.multipartResolver = multipartResolver;
	}

	public HPEQuoteIntegrationFacade getHpeQuoteIntegrationFacade()
	{
		return hpeQuoteIntegrationFacade;
	}

	public void setHpeQuoteIntegrationFacade(final HPEQuoteIntegrationFacade hpeQuoteIntegrationFacade)
	{
		this.hpeQuoteIntegrationFacade = hpeQuoteIntegrationFacade;
	}








}
