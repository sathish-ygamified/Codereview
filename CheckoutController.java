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

import de.hybris.platform.acceleratorfacades.flow.impl.SessionOverrideCheckoutFlowFacade;
import de.hybris.platform.acceleratorservices.controllers.page.PageType;
import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.consent.data.ConsentCookieData;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractCheckoutController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.ConsentForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.GuestRegisterForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.UpdateQuantityForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.validation.GuestRegisterValidator;
import de.hybris.platform.acceleratorstorefrontcommons.security.AutoLoginStrategy;
import de.hybris.platform.acceleratorstorefrontcommons.strategy.CustomerConsentDataStrategy;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.AbstractPageModel;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.consent.ConsentFacade;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.commercefacades.i18n.I18NFacade;
import de.hybris.platform.commercefacades.order.data.AbstractOrderData;
import de.hybris.platform.commercefacades.order.data.CartData;
import de.hybris.platform.commercefacades.order.data.OrderData;
import de.hybris.platform.commercefacades.order.data.OrderEntryData;
import de.hybris.platform.commercefacades.product.ProductFacade;
import de.hybris.platform.commercefacades.product.ProductOption;
import de.hybris.platform.commercefacades.product.data.ProductData;
import de.hybris.platform.commercefacades.user.data.AddressData;
import de.hybris.platform.commercefacades.user.data.CustomerData;
import de.hybris.platform.commercefacades.user.data.SystemManagerContactInfoData;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.commerceservices.order.CommerceCartModificationException;
import de.hybris.platform.commerceservices.order.CommerceSaveCartException;
import de.hybris.platform.commerceservices.util.ResponsiveUtils;
import de.hybris.platform.core.enums.PhoneContactInfoType;
import de.hybris.platform.core.model.media.MediaModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.order.InvalidCartException;
import de.hybris.platform.servicelayer.exceptions.ModelNotFoundException;
import de.hybris.platform.servicelayer.exceptions.UnknownIdentifierException;
import de.hybris.platform.util.Config;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.WebUtils;

import com.hpe.checkout.steps.validation.impl.HPEDefaultCheckoutPOFormValidator;
import com.hpe.controllers.ControllerConstants;
import com.hpe.facades.order.HPEOrderFacade;
import com.hpe.facades.order.impl.HPEB2BCheckoutFlowFacade;
import com.hpe.facades.orders.account.data.HPEAccountCustomFieldsData;
import com.hpe.facades.orders.data.HPEOrdersAccountData;
import com.hpe.facades.orders.data.HPEOrdersData;
import com.hpe.facades.quote.impl.HPEDefaultQuoteFacade;
import com.hpe.facades.user.ContactInfoFacade;
import com.hpe.facades.user.HPEUserFacade;
import com.hpe.quote.form.HPEOrdersAccountForm;
import com.hpe.quote.form.HPEOrdersBillAddressForm;
import com.hpe.quote.form.HPEOrdersShipAddressForm;
import com.hpe.quote.form.HpeOrdersForm;
import com.hpe.quote.form.SystemManagerContactInfoForm;
import com.hpe.util.HPEB2bCompanyUtils;
import com.hpe.util.HPEOrderFormDataBindingHelper;


/**
 * CheckoutController
 */
@SessionAttributes(
{ "hpeOrdersForm", "hpeOrdersData" })
@Controller
@RequestMapping(value = "/checkout")
public class CheckoutController extends AbstractCheckoutController
{
	private static final Logger LOG = Logger.getLogger(CheckoutController.class);
	/**
	 * We use this suffix pattern because of an issue with Spring 3.1 where a Uri value is incorrectly extracted if it
	 * contains on or more '.' characters. Please see https://jira.springsource.org/browse/SPR-6164 for a discussion on
	 * the issue and future resolution.
	 */
	private static final String ORDER_CODE_PATH_VARIABLE_PATTERN = "{orderCode:.*}";

	private static final String CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL = "orderConfirmation";
	private static final String CONTINUE_URL_KEY = "continueUrl";
	private static final String CONSENT_FORM_GLOBAL_ERROR = "consent.form.global.error";
	private static final String BINDING_RESULT_PREFIX = "org.springframework.validation.BindingResult.";

	private static final String HPE_ORDERS_FORM = "hpeOrdersForm";
	private static final String HPE_ORDERS_DATA = "hpeOrdersData";
	private static final String HPE_ORDERS_SHIPADDRESS_FORM = "hpeOrdersShipAddressForm";
	private static final String HPE_ORDERS_BILLADDRESS_FORM = "hpeOrdersBillAddressForm";
	private static final String HPE_ORDERS_SHIPPINGADDRESS_DATA = "hpeShipingAddressData";
	private static final String HPE_ORDERS_BILLINGADDRESS_DATA = "hpeBillingAddressData";
	private static final String HPE_ORDERS_ACCOUNT_FORM = "hpeOrdersAccountForm";

	private static final String HPE_CARTDATA = "cartData";

	private static final String ORDERS_SHIPPING_ADDRESS = "OrdersUnitShipping";
	private static final String ORDERS_BILLING_ADDRESS = "OrdersUnitBilling";
	private static final String SYSTEM_MANAGER_FORM = "systemManagerForm";
	private static final String REDIRECT_EDIT_ACCOUNT_URL = REDIRECT_PREFIX + "/checkout/editCheckoutAccount/%s/";
	protected static final String CHECKOUT_PURCHASE_ORDER_CONFIRMATION_CMS_PAGE_LABEL = "checkout.purchaseorder.create.page.label";
	private static final String QUOTE_UPLOAD_DOCS_FILE_PATH = "media.read.dir";
	private static final String SAVE_DIR = "sys_master";
	private static final int MAX_UPLOAD_SIZE = 5;
	private static final String CART_CODE_PATH_VARIABLE_PATTERN = "{cartCode:.*}";
	private static final String ADDRESS_ID_PATH_VARIABLE_PATTERN = "{addressId:.*}";
	private static final String REDIRECT_CHECKOUT_FLOW_VIEW_URL = REDIRECT_PREFIX + "/checkout/%s/createCheckoutView";
	private static final String REDIRECT_ORDER_SUMMARY_URL = REDIRECT_PREFIX + "/checkout/orderConfirmation/%s";
	private static final String REDIRECT_QUOTE_FLOW_VIEW_URL = REDIRECT_PREFIX + "/quote/%s/createInitiateQuote";
	private static final String REDIRECT_FAVORITE_FLOW_VIEW_URL = REDIRECT_PREFIX + "/cart/save";
	//private static final String REDIRECT_FAVORITE_FLOW_VIEW_URL = REDIRECT_PREFIX + "/my-account/favorites/%s";
	private static final String QUOTE_CREATION_PAGE = "/quote/%s/createInitiateQuote";

	private static final String HPECHECKOUT_PO_CREATE_SUCCESS = "hpe.checkoutpo.create.success";
	private static final String HPECHECKOUT_PO_CREATE_FAIL = "hpe.checkoutpo.create.fail";
	private static final String REORDER = "REORDER";
	private static final String QUOTE = "QUOTE";
	private static final String CHECKOUT = "CHECKOUT";
	private static final String FAVORITE = "FAVORITE";


	@Resource(name = "productFacade")
	private ProductFacade productFacade;

	@Resource(name = "hpeDefaultOrderFacade")
	private HPEOrderFacade hpeDefaultOrderFacade;

	@Resource(name = "guestRegisterValidator")
	private GuestRegisterValidator guestRegisterValidator;

	@Resource(name = "autoLoginStrategy")
	private AutoLoginStrategy autoLoginStrategy;

	@Resource(name = "consentFacade")
	protected ConsentFacade consentFacade;

	@Resource(name = "customerConsentDataStrategy")
	protected CustomerConsentDataStrategy customerConsentDataStrategy;

	@Resource(name = "hpeUserFacade")
	private HPEUserFacade hpeUserFacade;

	@Resource(name = "hpeDefaultQuoteFacade")
	private HPEDefaultQuoteFacade hpeDefaultQuoteFacade;

	@Resource(name = "i18NFacade")
	private I18NFacade i18NFacade;

	@Resource(name = "customerFacade")
	private CustomerFacade customerFacade;

	@Resource(name = "hpeDefaultB2BCheckoutFlowFacade")
	private HPEB2BCheckoutFlowFacade hpeDefaultB2BCheckoutFlowFacade;

	@Resource(name = "hpeDefaultCheckoutPOFormValidator")
	private HPEDefaultCheckoutPOFormValidator hpeDefaultCheckoutPOFormValidator;

	@Resource(name = "hpeB2bCompanyUtils")
	private HPEB2bCompanyUtils hpeB2bCompanyUtils;

	@Resource(name = "contactInfoFacade")
	private ContactInfoFacade contactInfoFacade;

	@Resource(name = "orderFormDataBindingHelper")
	private HPEOrderFormDataBindingHelper orderFormDataBindingHelper;

	@ExceptionHandler(ModelNotFoundException.class)
	public String handleModelNotFoundException(final ModelNotFoundException exception, final HttpServletRequest request)
	{
		request.setAttribute("message", exception.getMessage());
		return FORWARD_PREFIX + "/404";
	}



	@RequestMapping(method = RequestMethod.GET)
	public String checkout(final RedirectAttributes redirectModel)
	{
		if (getCheckoutFlowFacade().hasValidCart())
		{
			final String cartCode = getCheckoutFlowFacade().getCheckoutCart().getCode();
			return String.format(REDIRECT_CHECKOUT_FLOW_VIEW_URL, urlEncode(cartCode));
		}

		LOG.info("Missing, empty or unsupported cart");

		// No session cart or empty session cart. Bounce back to the cart page.
		return REDIRECT_PREFIX + "/cart";
	}


	@RequestMapping(value = "/orderConfirmation/" + ORDER_CODE_PATH_VARIABLE_PATTERN, method = RequestMethod.POST)
	public String orderConfirmation(final GuestRegisterForm form, final BindingResult bindingResult, final Model model,
			final HttpServletRequest request, final HttpServletResponse response, final RedirectAttributes redirectModel)
			throws CMSItemNotFoundException
	{
		getGuestRegisterValidator().validate(form, bindingResult);
		return processRegisterGuestUserRequest(form, bindingResult, model, request, response, redirectModel);
	}

	protected String processRegisterGuestUserRequest(final GuestRegisterForm form, final BindingResult bindingResult,
			final Model model, final HttpServletRequest request, final HttpServletResponse response,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		if (bindingResult.hasErrors())
		{
			form.setTermsCheck(false);
			GlobalMessages.addErrorMessage(model, "form.global.error");
			return processOrderCode(form.getOrderCode(), model);
		}
		try
		{
			getCustomerFacade().changeGuestToCustomer(form.getPwd(), form.getOrderCode());
			getAutoLoginStrategy().login(getCustomerFacade().getCurrentCustomer().getUid(), form.getPwd(), request, response);
			getSessionService().removeAttribute(WebConstants.ANONYMOUS_CHECKOUT);
		}
		catch (final DuplicateUidException e)
		{
			// User already exists
			LOG.warn("guest registration failed: " + e);
			form.setTermsCheck(false);
			model.addAttribute(new GuestRegisterForm());
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					"guest.checkout.existingaccount.register.error", new Object[]
					{ form.getUid() });
			return REDIRECT_PREFIX + request.getHeader("Referer");
		}

		// Consent form data
		try
		{
			final ConsentForm consentForm = form.getConsentForm();
			if (consentForm != null && consentForm.getConsentGiven())
			{
				getConsentFacade().giveConsent(consentForm.getConsentTemplateId(), consentForm.getConsentTemplateVersion());
			}
		}
		catch (final Exception e)
		{
			LOG.error("Error occurred while creating consents during registration", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, CONSENT_FORM_GLOBAL_ERROR);
		}

		// save anonymous-consent cookies as ConsentData
		final Cookie cookie = WebUtils.getCookie(request, WebConstants.ANONYMOUS_CONSENT_COOKIE);
		if (cookie != null)
		{
			try
			{
				final ObjectMapper mapper = new ObjectMapper();
				final List<ConsentCookieData> consentCookieDataList = Arrays.asList(mapper.readValue(
						URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8.displayName()), ConsentCookieData[].class));
				consentCookieDataList.stream().filter(consentData -> WebConstants.CONSENT_GIVEN.equals(consentData.getConsentState()))
						.forEach(consentData -> consentFacade.giveConsent(consentData.getTemplateCode(),
								Integer.valueOf(consentData.getTemplateVersion())));
			}
			catch (final UnsupportedEncodingException e)
			{
				LOG.error(String.format("Cookie Data could not be decoded : %s", cookie.getValue()), e);
			}
			catch (final IOException e)
			{
				LOG.error("Cookie Data could not be mapped into the Object", e);
			}
			catch (final Exception e)
			{
				LOG.error("Error occurred while creating Anonymous cookie consents", e);
			}
		}

		customerConsentDataStrategy.populateCustomerConsentDataInSession();

		return REDIRECT_PREFIX + "/";
	}



	protected void processEmailAddress(final Model model, final OrderData orderDetails)
	{
		final String uid;

		if (orderDetails.isGuestCustomer() && !model.containsAttribute("guestRegisterForm"))
		{
			final GuestRegisterForm guestRegisterForm = new GuestRegisterForm();
			guestRegisterForm.setOrderCode(orderDetails.getGuid());
			uid = orderDetails.getPaymentInfo().getBillingAddress().getEmail();
			guestRegisterForm.setUid(uid);
			model.addAttribute(guestRegisterForm);
		}
		else
		{
			uid = orderDetails.getUser().getUid();
		}
		model.addAttribute("email", uid);
	}


	/**
	 * Checkout flow journey to place a order
	 *
	 * @author SHAAIEE
	 * @param cartCode
	 * @param model
	 * @param hpeOrdersForm
	 * @param hpeOrdersData
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException
	 * @throws CommerceSaveCartException
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/" + CART_CODE_PATH_VARIABLE_PATTERN + "/createCheckoutView", method = RequestMethod.GET)
	public String showCreateCheckoutView(@PathVariable("cartCode") final String cartCode, final Model model,
			@Valid final HpeOrdersForm hpeOrdersForm, @Valid final HPEOrdersData hpeOrdersData)
			throws CMSItemNotFoundException, CommerceSaveCartException
	{
		LOG.info("showCreatePurchaseOrderView method starts");
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
			prepareDataForPage(hpeOrdersForm, hpeOrdersData, model);
			model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
			model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		}
		//populateSystemManager(model, getCartFacade().getSessionCart());
		final ContentPageModel contentPage = getCmsPageService().getPageForLabel(
				getSiteConfigService().getString(CHECKOUT_PURCHASE_ORDER_CONFIRMATION_CMS_PAGE_LABEL, "orderConfirmation"));
		storeCmsPageInModel(model, contentPage);
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		LOG.info("showCreatePurchaseOrderView method is ends-----getViewForPage---->");

		return ControllerConstants.Views.Pages.CheckoutFlow.HpeCreateCheckoutFlowViewPage;
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
	@RequestMapping(value = "/editCheckoutAccount/{acctUserUid}", method =
	{ RequestMethod.GET, RequestMethod.POST })
	@RequireHardLogIn
	public String editCheckoutAccountInfo(@PathVariable("acctUserUid") final String acctUserUid,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model, @ModelAttribute final HPEOrdersData hpeOrdersData)
			throws CMSItemNotFoundException
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

		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Checkout.HpeEditOrdersAccountPage;
	}



	@RequestMapping(value = "/editCheckoutShipAddress", method = RequestMethod.GET)
	public String editCheckoutFlowShippingAddress(@RequestParam("addressId") final String addressId,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model, @ModelAttribute final HPEOrdersData hpeOrdersData)
			throws CMSItemNotFoundException
	{
		LOG.info("editCheckoutFlowShippingAddress method starts==addressId======>" + addressId);
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

		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Checkout.HpeEditOrdersShippingAddressPage;

	}

	/**
	 * Method to edit PO Billing Address Information.
	 *
	 * @param hpeOrdersForm
	 * @param model
	 * @param redirectModel
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/editCheckoutBillAddress", method = RequestMethod.GET)
	public String editCheckoutFlowBillingAddress(@RequestParam("addressId") final String addressId,
			@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model, @ModelAttribute final HPEOrdersData hpeOrdersData)
			throws CMSItemNotFoundException
	{
		LOG.info("editCheckoutFlowBillingAddress method starts==addressId======>" + addressId);
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

		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.Checkout.HpeEditOrdersBillingAddressPage;
	}


	@RequireHardLogIn
	@RequestMapping(value = "/saveAcctTransaction", method = RequestMethod.POST)
	public String saveAccountTransaction(@RequestParam("acctUserUid") final String acctUserUid,
			@ModelAttribute("hpeOrdersForm") final HpeOrdersForm hpeOrdersForm, final Model model,
			@ModelAttribute("hpeOrdersData") final HPEOrdersData hpeOrdersData, final BindingResult bindingResult)
			throws CMSItemNotFoundException
	{
		LOG.info("saveQuoteAccountTransaction method starts==acctUserUid==" + acctUserUid);
		if (bindingResult.hasErrors())
		{
			return "SaveAccountTransactionError";
		}
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
				final Set<Map.Entry<MultipartFile, String>> setMap = mediaFileUrlMap.entrySet();
				final Iterator<Map.Entry<MultipartFile, String>> itr = setMap.iterator();
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

		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);

		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));

		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.CheckoutFlow.HpeCreateCheckoutFlowViewPage;
	}

	/**
	 * @author SHAAIEE
	 * @param type
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
			@ModelAttribute("hpeOrdersData") final HPEOrdersData hpeOrdersData, final Model model, final BindingResult bindingResult)
			throws CMSItemNotFoundException
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

		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.CheckoutFlow.HpeCreateCheckoutFlowViewPage;
	}

	protected void createProductEntryList(final Model model, final CartData cartData)
	{
		boolean hasPickUpCartEntries = false;
		if (cartData.getEntries() != null && !cartData.getEntries().isEmpty())
		{
			for (final OrderEntryData entry : cartData.getEntries())
			{
				if (!hasPickUpCartEntries && entry.getDeliveryPointOfService() != null)
				{
					hasPickUpCartEntries = true;
				}
				final UpdateQuantityForm uqf = new UpdateQuantityForm();
				uqf.setQuantity(entry.getQuantity());
				model.addAttribute("updateQuantityForm" + entry.getEntryNumber(), uqf);
			}
		}
		model.addAttribute("cartData", cartData);
		model.addAttribute("hasPickUpCartEntries", Boolean.valueOf(hasPickUpCartEntries));
	}

	/**
	 * Upload the Quote Account Upload files list
	 *
	 * @author SHAAIEE
	 * @param model
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
			return "Upload the Document Error";
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

					//media url
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

							//media url
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
			//}//for multipart file

		}
		if (hpeOrdersForm.getHpeOrdersAccountForm().getAccountUidEmail() != null)
		{
			acctUserUid = hpeOrdersForm.getHpeOrdersAccountForm().getAccountUidEmail();
		}
		return String.format(REDIRECT_EDIT_ACCOUNT_URL, urlEncode(acctUserUid));
	}

	/***
	 * Cancel the Transaction Page
	 *
	 * @param hpeOrdersForm
	 * @param model
	 * @param hpeOrdersData
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequestMapping(value = "/cancelCheckoutView", method = RequestMethod.GET)
	@RequireHardLogIn
	public String cancelCheckoutView(@ModelAttribute final HpeOrdersForm hpeOrdersForm, final Model model,
			@ModelAttribute final HPEOrdersData hpeOrdersData) throws CMSItemNotFoundException
	{

		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			orderFormDataBindingHelper.populateFormAccountToData(model, hpeOrdersAccountForm, hpeOrdersData);
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

		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		return ControllerConstants.Views.Pages.CheckoutFlow.HpeCreateCheckoutFlowViewPage;

	}

	private void assignedPODetails(final HpeOrdersForm hpeOrdersForm, final HPEOrdersData hpeOrdersData, final Model model)
	{
		if (StringUtils.isNotEmpty(hpeOrdersForm.getPurchaseOrderNumber()))
		{
			hpeOrdersForm.setPurchaseOrderNumber(hpeOrdersForm.getPurchaseOrderNumber());
			LOG.debug("purchaseOrderNumber------------>>>>" + hpeOrdersForm.getPurchaseOrderNumber());
		}
		if (StringUtils.isNotEmpty(hpeOrdersForm.getPurchaseOrderName()))
		{
			hpeOrdersForm.setPurchaseOrderName(hpeOrdersForm.getPurchaseOrderName());
			LOG.debug("PurchaseOrderName------------>>>>" + hpeOrdersForm.getPurchaseOrderName());
		}

		if (StringUtils.isNotEmpty(hpeOrdersForm.getPurchaseOrderNumber()))
		{
			hpeOrdersData.setPurchaseOrderNumber(hpeOrdersForm.getPurchaseOrderNumber());
		}
		if (StringUtils.isNotEmpty(hpeOrdersForm.getPurchaseOrderName()))
		{
			hpeOrdersData.setPurchaseOrderName(hpeOrdersForm.getPurchaseOrderName());
		}

	}

	/***
	 * Method is used to call OOB Place Order
	 *
	 * @author SHAAIEE
	 * @param hpeOrdersForm
	 * @param hpeOrdersData
	 * @param model
	 * @param bindingResult
	 * @param redirectModel
	 * @param sessionStatus
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@RequireHardLogIn
	@RequestMapping(value = "/checkoutPlaceOrderSummary", method = RequestMethod.POST)
	public String showCheckoutPlaceOrderSummary(@ModelAttribute final HpeOrdersForm hpeOrdersForm,
			@ModelAttribute final HPEOrdersData hpeOrdersData, final Model model, final BindingResult bindingResult,
			final RedirectAttributes redirectModel, final SessionStatus sessionStatus) throws CMSItemNotFoundException
	{
		// here we set the price dislaimer flag and message to model - business logic inside
		hpeB2bCompanyUtils.setPriceDisclaimerToModel(model);
		OrderData orderData = null;
		assignedPODetails(hpeOrdersForm, hpeOrdersData, model);
		final String cartCode = getCheckoutFlowFacade().getCheckoutCart().getCode();
		if (hpeOrdersForm.getHpeOrdersAccountForm() != null)
		{
			final HPEOrdersAccountForm hpeOrdersAccountForm = hpeOrdersForm.getHpeOrdersAccountForm();
			hpeOrdersForm.setHpeOrdersAccountForm(hpeOrdersAccountForm);
			final HPEOrdersAccountData hpeAccountQuoteData = orderFormDataBindingHelper.populateFormAccountToData(model,
					hpeOrdersAccountForm, hpeOrdersData);
			hpeOrdersData.setHpeOrdersAccountData(hpeAccountQuoteData);
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

		model.addAttribute(HPE_ORDERS_FORM, hpeOrdersForm);
		model.addAttribute(HPE_ORDERS_DATA, hpeOrdersData);
		if (CollectionUtils.isEmpty(hpeOrdersData.getHpeCartData().getEntries()))
		{
			return REDIRECT_PREFIX + "/cart";
		}
		hpeDefaultCheckoutPOFormValidator.validate(hpeOrdersForm, bindingResult);
		if (bindingResult.hasErrors())
		{
			for (final ObjectError error : bindingResult.getAllErrors())
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, error.getCode());
			}
			redirectModel.addFlashAttribute("hpeOrdersForm", hpeOrdersForm);
			return String.format(REDIRECT_CHECKOUT_FLOW_VIEW_URL, urlEncode(cartCode));
		}
		try
		{
			getHpeDefaultB2BCheckoutFlowFacade().saveCheckoutFlowPODetails(hpeOrdersData);
			orderData = getHpeDefaultB2BCheckoutFlowFacade().placeOrder();
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER, HPECHECKOUT_PO_CREATE_SUCCESS);
		}
		catch (final Exception e)
		{
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, HPECHECKOUT_PO_CREATE_FAIL);
			if (hpeOrdersForm != null)
			{
				sessionStatus.setComplete();
			}
			LOG.info("After order placement exception ---checkout.placeOrder.failed---------->>>>" + e);
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		LOG.info("orderData.getCode()----SUCCESS-----::::" + orderData.getCode());
		sessionStatus.setComplete();
		return REDIRECT_PREFIX + "/checkout/orderConfirmation/" + orderData.getCode();
	}


	@RequestMapping(value = "/orderConfirmation/" + ORDER_CODE_PATH_VARIABLE_PATTERN, method = RequestMethod.GET)
	@RequireHardLogIn
	public String orderConfirmation(@PathVariable("orderCode") final String orderCode, final Model model)
			throws CMSItemNotFoundException
	{
		LOG.info("orderConfirmation===orderCode==" + orderCode);
		SessionOverrideCheckoutFlowFacade.resetSessionOverrides();
		return processOrderCode(orderCode, model);
	}

	protected String processOrderCode(final String orderCode, final Model model) throws CMSItemNotFoundException
	{
		final OrderData orderData;
		try
		{
			orderData = hpeDefaultOrderFacade.getHPEOrderDetailsForCode(orderCode);
		}
		catch (final UnknownIdentifierException e)
		{
			LOG.warn("Attempted to load an order confirmation that does not exist or is not visible. Redirect to home page.");
			return REDIRECT_PREFIX + ROOT;
		}
		if (orderData.isGuestCustomer() && !StringUtils.substringBefore(orderData.getUser().getUid(), "|")
				.equals(getSessionService().getAttribute(WebConstants.ANONYMOUS_CHECKOUT_GUID)))
		{
			return getCheckoutRedirectUrl();
		}
		if (orderData.getEntries() != null && !orderData.getEntries().isEmpty())
		{
			for (final OrderEntryData entry : orderData.getEntries())
			{
				final String productCode = entry.getProduct().getCode();
				final ProductData product = productFacade.getProductForCodeAndOptions(productCode,
						Arrays.asList(ProductOption.BASIC, ProductOption.PRICE, ProductOption.CATEGORIES));
				entry.setProduct(product);
			}
		}
		model.addAttribute("orderData", orderData);
		model.addAttribute("ordersMediaModelList", orderData.getHpeOrdersAccountData().getOrdersMediaModelList());
		if (orderData.getHpeOrdersAccountData().getOrdersMediaModelList() != null)
		{
			final Collection<MediaModel> quoteMediaModelList = orderData.getHpeOrdersAccountData().getOrdersMediaModelList();
			final Iterator<MediaModel> itrMediaFile = quoteMediaModelList.iterator();
			final HashMap<String, String> ordersMediaMap = new HashMap<String, String>();
			while (itrMediaFile.hasNext())
			{
				final MediaModel mediaModel = itrMediaFile.next();
				if (mediaModel != null)
				{
					orderData.getHpeOrdersAccountData().setOrdersMediaPK(mediaModel.getRealFileName());
					orderData.getHpeOrdersAccountData().setOrdersMediaUrlLink(mediaModel.getDownloadURL());
					model.addAttribute("ordersMediaFileName", orderData.getHpeOrdersAccountData().getOrdersMediaPK());
					model.addAttribute("ordersMediaFileUrl", orderData.getHpeOrdersAccountData().getOrdersMediaUrlLink());
					ordersMediaMap.put(mediaModel.getRealFileName(), mediaModel.getDownloadURL());
					model.addAttribute("ordersMediaMap", ordersMediaMap);
				}
			}
		}
		model.addAttribute("orderCode", orderCode);
		model.addAttribute("orderDataPromotions", orderData.getAppliedOrderPromotions());
		model.addAttribute("allItems", orderData.getEntries());
		model.addAttribute("deliveryAddress", orderData.getDeliveryAddress());
		model.addAttribute("deliveryMode", orderData.getDeliveryMode());
		model.addAttribute("paymentInfo", orderData.getPaymentInfo());
		model.addAttribute("pageType", PageType.ORDERCONFIRMATION.name());
		populateSystemManager(model, orderData);
		//processEmailAddress(model, orderDetails);

		final String continueUrl = (String) getSessionService().getAttribute(WebConstants.CONTINUE_URL);
		model.addAttribute(CONTINUE_URL_KEY, (continueUrl != null && !continueUrl.isEmpty()) ? continueUrl : ROOT);
		final AbstractPageModel cmsPage = getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL);
		storeCmsPageInModel(model, cmsPage);
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
		if (ResponsiveUtils.isResponsive())
		{
			return ControllerConstants.Views.Pages.CheckoutFlow.HpeCreateCheckoutFlowSummaryPage;
		}

		return ControllerConstants.Views.Pages.CheckoutFlow.HpeCreateCheckoutFlowSummaryPage;
	}

	protected void prepareDataForPage(final HpeOrdersForm hpeOrdersForm, final HPEOrdersData hpeOrdersData, final Model model)
			throws CMSItemNotFoundException
	{
		model.addAttribute("pageType", PageType.ORDERCONFIRMATION.name());
		final UpdateQuantityForm updateQuantityForm = new UpdateQuantityForm();
		updateQuantityForm.setQuantity(0L);
		model.addAttribute("updateQuantityForm", updateQuantityForm);
		setCartDataInfoView(hpeOrdersForm, hpeOrdersData, model);

	}

	/**
	 * Set the CartData entries in the model object.
	 *
	 * @author shaaiee
	 * @param hpeOrdersForm
	 * @param model
	 * @throws CMSItemNotFoundException
	 */
	private CartData setCartDataInfoView(final HpeOrdersForm hpeOrdersForm, final HPEOrdersData hpeOrdersData, final Model model)
			throws CMSItemNotFoundException
	{
		final CartData cartData = getCheckoutFlowFacade().getCheckoutCart();
		LOG.info("hpeCartData-getCheckoutFlowFacade-----" + cartData);
		if (cartData != null)
		{
			createProductEntryList(model, cartData);
		}
		hpeOrdersData.setHpeCartData(cartData);
		hpeOrdersForm.setHpeCartData(cartData);
		model.addAttribute(HPE_CARTDATA, cartData);
		populateSystemManager(model, cartData);
		return cartData;
	}


	/**
	 * System manager contact info
	 *
	 * @param model
	 * @param abstractOrder
	 */
	private void populateSystemManager(final Model model, final AbstractOrderData abstractOrder)
	{
		model.addAttribute("abstractOrder", abstractOrder);

		final SystemManagerContactInfoData contactInfoData = contactInfoFacade.getSystemManagerContactInfo(abstractOrder);
		if (contactInfoData != null)
		{
			if (model.asMap().containsKey(BINDING_RESULT_PREFIX + SYSTEM_MANAGER_FORM))
			{
				final BindingResult bindingResult = (BindingResult) model.asMap().get(BINDING_RESULT_PREFIX + SYSTEM_MANAGER_FORM);
				if (bindingResult.hasErrors())
				{
					model.addAttribute(SYSTEM_MANAGER_FORM + "HasError", true);
				}

				if (model.asMap().containsKey(SYSTEM_MANAGER_FORM))
				{
					final SystemManagerContactInfoForm contactInfoForm = (SystemManagerContactInfoForm) model.asMap()
							.get(SYSTEM_MANAGER_FORM);
					orderFormDataBindingHelper.populateDisabledFields(contactInfoForm, i18NFacade);
					model.addAttribute(SYSTEM_MANAGER_FORM, contactInfoForm);
				}
			}
			else
			{
				final SystemManagerContactInfoForm contactInfoForm = orderFormDataBindingHelper
						.convertSystemManagerData(contactInfoData);
				model.addAttribute(SYSTEM_MANAGER_FORM, contactInfoForm);
			}

			final List<SystemManagerContactInfoData> contactInfoDatas = contactInfoFacade
					.getSystemManagerContactInfos(abstractOrder);
			final List<AddressData> contactInfoAddresses = contactInfoDatas.stream().map(contactInfo -> contactInfo.getAddress())
					.collect(Collectors.toList());
			final List<SelectOption> formattedCIAddresses = populateAddresses(contactInfoAddresses);
			model.addAttribute("formattedCIAddresses", formattedCIAddresses);
		}
	}

	@RequestMapping(value = "/po/systemManager/" + CART_CODE_PATH_VARIABLE_PATTERN, method = RequestMethod.POST)
	@RequireHardLogIn
	public String saveSystemManager(@Valid final SystemManagerContactInfoForm contactInfoForm, final BindingResult bindingResult,
			@PathVariable("cartCode") final String cartCode, final RedirectAttributes redirectModel)
	{
		if (bindingResult.hasErrors())
		{
			redirectModel.addFlashAttribute(BINDING_RESULT_PREFIX + SYSTEM_MANAGER_FORM, bindingResult);
			redirectModel.addFlashAttribute(SYSTEM_MANAGER_FORM, contactInfoForm);
		}
		else
		{
			final AddressData addressData = orderFormDataBindingHelper.convertSystemManagerForm(contactInfoForm, i18NFacade);
			try
			{
				contactInfoFacade.addSystemManagerContactInfo(addressData, contactInfoForm.getSupportAgreementID(),
						contactInfoForm.getPhone(), PhoneContactInfoType.WORK);
			}
			catch (final Exception e)
			{
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, e.getMessage());
			}
		}
		return String.format(REDIRECT_CHECKOUT_FLOW_VIEW_URL, urlEncode(cartCode));
	}

	@RequestMapping(value = "/po/systemManager/addresses/"
			+ ADDRESS_ID_PATH_VARIABLE_PATTERN, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	@RequireHardLogIn
	public SystemManagerContactInfoData getSystemManagerContactInfo(@PathVariable("addressId") final String addressId)
	{
		final SystemManagerContactInfoData contactInfoData = contactInfoFacade.getSystemManagerContactInfo(addressId);
		return contactInfoData;
	}

	/**
	 * Method is used to create a cart from the Order
	 *
	 * @param orderCode
	 * @param action
	 * @param redirectModel
	 * @param model
	 * @param request
	 * @param response
	 * @return
	 * @throws CMSItemNotFoundException
	 * @throws InvalidCartException
	 *            *
	 * @throws CommerceCartModificationException
	 */
	@RequestMapping(value = "/{action}/saveReOrders", method =
	{ RequestMethod.GET, RequestMethod.POST })
	@RequireHardLogIn
	public String saveReorders(@RequestParam(value = "orderCode") final String orderCode,
			@PathVariable("action") final String action, final RedirectAttributes redirectModel, final Model model,
			final HttpServletRequest request, final HttpServletResponse response)
			throws CMSItemNotFoundException, InvalidCartException, CommerceCartModificationException
	{
		final String contextPath = request.getContextPath();
		final String basePath = orderFormDataBindingHelper.getBasePath(request);
		CartModel cartModel = null;
		try
		{
			LOG.info("saveReorders===orderCode==" + orderCode);
			LOG.info("saveReorders===action==" + action);
			;
			cartModel = getHpeDefaultB2BCheckoutFlowFacade().reorderFlow(orderCode);
		}
		catch (final IllegalArgumentException e)
		{
			LOG.error(String.format("Unable to reorder %s", orderCode), e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER, "order.reorder.error", null);
			return REDIRECT_ORDER_SUMMARY_URL;
		}
		LOG.info("saveReorders===CartModel::code==" + cartModel.getCode());
		if (action.equalsIgnoreCase(REORDER))
		{
			return String.format(REDIRECT_CHECKOUT_FLOW_VIEW_URL, urlEncode(cartModel.getCode()));
		}
		else if (action.equalsIgnoreCase(QUOTE))
		{
			final String saveQuoteRedirect = basePath + contextPath + "/quote/" + cartModel.getCode() + "/createInitiateQuote";
			model.addAttribute("saveQuoteRedirect", saveQuoteRedirect);
			LOG.info("saveQuoteRedirect::" + saveQuoteRedirect);
			return REDIRECT_PREFIX + saveQuoteRedirect;
			//return REDIRECT_PREFIX + "/quote/" + cartModel.getCode() + "/createInitiateQuote";
			//return String.format(REDIRECT_QUOTE_FLOW_VIEW_URL, urlEncode(cartModel.getCode()));
		}
		else if (action.equalsIgnoreCase(FAVORITE))
		{
			final String savefavoriteRedirect = basePath + contextPath + "/cart/save";
			LOG.info("savefavoriteRedirect::" + savefavoriteRedirect);
			model.addAttribute("savefavoriteRedirect", savefavoriteRedirect);
			return REDIRECT_PREFIX + savefavoriteRedirect;
			//	return String.format(REDIRECT_FAVORITE_FLOW_VIEW_URL, urlEncode(cartModel.getCode()));
		}
		storeCmsPageInModel(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		setUpMetaDataForContentPage(model, getContentPageForLabelOrId(CHECKOUT_ORDER_CONFIRMATION_CMS_PAGE_LABEL));
		return null;
	}



	protected GuestRegisterValidator getGuestRegisterValidator()
	{
		return guestRegisterValidator;
	}

	protected AutoLoginStrategy getAutoLoginStrategy()
	{
		return autoLoginStrategy;
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
			LOG.info("populateAddresses==addressData.getId()==>" + addressData.getId());
			LOG.info("populateAddresses==addressData.getCompanyName()==>" + addressData.getCompanyName());
			addresses.add(new SelectOption(addressData.getId(), addressData.getFormattedAddress()));
		}
		return addresses;
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
	 * @return the hpeDefaultB2BCheckoutFlowFacade
	 */
	public HPEB2BCheckoutFlowFacade getHpeDefaultB2BCheckoutFlowFacade()
	{
		return hpeDefaultB2BCheckoutFlowFacade;
	}

	/**
	 * @param hpeDefaultB2BCheckoutFlowFacade
	 *           the hpeDefaultB2BCheckoutFlowFacade to set
	 */
	public void setHpeDefaultB2BCheckoutFacade(final HPEB2BCheckoutFlowFacade hpeDefaultB2BCheckoutFlowFacade)
	{
		this.hpeDefaultB2BCheckoutFlowFacade = hpeDefaultB2BCheckoutFlowFacade;
	}


}
