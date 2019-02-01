/* [y] hybris Platform
 *
 * Copyright (c) 2018 SAP SE or an SAP affiliate company.  All rights reserved.
 *
 * This software is the confidential and proprietary information of SAP
 * ("Confidential Information"). You shall not disclose such Confidential
 * Information and shall use it only in accordance with the terms of the
 * license agreement you entered into with SAP.
 */
package com.hpe.controllers.pages;

import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.b2b.company.B2BCommerceUnitService;
import de.hybris.platform.b2b.model.B2BCustomerModel;
import de.hybris.platform.b2b.model.B2BUnitModel;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.AbstractPageModel;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.storesession.data.LanguageData;
import de.hybris.platform.commercefacades.user.data.CountryData;
import de.hybris.platform.commercefacades.user.data.RegionData;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.core.model.user.CustomerModel;
import de.hybris.platform.store.services.BaseStoreService;
import de.hybris.platform.util.Config;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.hpe.b2bstorefront.forms.HpeAddressForm;
import com.hpe.b2bstorefront.forms.InviteByEmailCustomerForm;
import com.hpe.controllers.ControllerConstants;
import com.hpe.controllers.HPEB2BControllerConstants;
import com.hpe.controllers.HPEB2BStorefrontConstant;
import com.hpe.core.b2bunit.dao.HPEB2BUnitDao;
import com.hpe.facades.addressdoctor.HPEAddressDoctorIntegrationFacade;
import com.hpe.facades.country.HPECountryFacade;
import com.hpe.facades.hpepassport.HPEPassportIntegrationFacade;
import com.hpe.facades.region.HPERegionFacade;
import com.hpe.facades.registration.data.HPERegisterData;
import com.hpe.facades.user.HPEB2BUserFacade;
import com.hpe.facades.user.HPEUserFacade;
import com.hpe.facades.util.GTSRequestData;
import com.hpe.facades.util.HPERegistrationUtil;
import com.hpe.hpepassport.form.data.HPERegisterInputForm;
import com.hpe.security.HPEB2BRegisterForm;
import com.hpe.util.GTSUtil;


/**
 * Register Controller for mobile. Handles login and register for the account flow.
 */
@Controller
@RequestMapping(value = "/registerUser")
public class RegisterPageController extends HPEAbstractRegisterPageController
{
	private static final Logger LOG = Logger.getLogger(RegisterPageController.class);
	private static final String GTS_FAIL_REDIRECT_URL = "gts.fail.redirect.url";
	private static final String DEFAULT_GTS_FAIL_REDIRECT_URL = "http://www.hpe.com";
	private static final String GTS_CHECK_ENABLED = "gts.login.ip.check";
	private static final String TRUE = "true";
	private static final String ERROR = "error";
	private static final String ROOT = "root";

	private HttpSessionRequestCache httpSessionRequestCache;
	@Resource(name = "baseStoreService")
	private BaseStoreService baseStoreService;

	@Resource(name = "hpeCountryFacade")
	private HPECountryFacade hpeCountryFacade;

	@Resource(name = "hpeRegionFacade")
	private HPERegionFacade hpeRegionFacade;

	@Resource(name = "hpePassportIntegrationFacade")
	public HPEPassportIntegrationFacade hpePassportIntegrationFacade;

	@Resource(name = "hpeAddressDoctorIntegrationFacade")
	public HPEAddressDoctorIntegrationFacade hpeAddressDoctorIntegrationFacade;

	@Resource(name = "b2bCommerceUnitService")
	public B2BCommerceUnitService b2BCommerceUnitService;

	@Resource(name = "hpeRegistrationUtil")
	public HPERegistrationUtil hpeRegistrationUtil;

	@Resource(name = "hPEB2BUserFacade")
	private HPEB2BUserFacade hPEB2BUserFacade;

	@Resource(name = "accountBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

	@Resource(name = "hpeUserFacade")
	private HPEUserFacade hpeUserFacade;

	@Resource(name = "hPEB2BUnitDao")
	private HPEB2BUnitDao hPEB2BUnitDao;

	@Override
	protected AbstractPageModel getCmsPage() throws CMSItemNotFoundException
	{
		return getContentPageForLabelOrId("register");
	}

	@Override
	protected String getSuccessRedirect(final HttpServletRequest request, final HttpServletResponse response)
	{
		if (httpSessionRequestCache.getRequest(request, response) != null)
		{
			return httpSessionRequestCache.getRequest(request, response).getRedirectUrl();
		}
		return "/";
	}

	@Override
	protected String getView()
	{
		return ControllerConstants.Views.Pages.Account.AccountRegisterPage;
	}

	@Resource(name = "httpSessionRequestCache")
	public void setHttpSessionRequestCache(final HttpSessionRequestCache accHttpSessionRequestCache)
	{
		this.httpSessionRequestCache = accHttpSessionRequestCache;
	}

	/**
	 * Method for displaying country code and list of region code while returning to register page.
	 *
	 * @param model
	 * @return model
	 *
	 **/

	@GetMapping
	public String doRegister(final Model model) throws CMSItemNotFoundException
	{
		final List<CountryData> countryList = hpeCountryFacade.findCountries();
		countryList.sort(Comparator.comparing(CountryData::getName, Comparator.nullsFirst(Comparator.naturalOrder())));
		model.addAttribute(HPEB2BStorefrontConstant.COUNTRY_LIST, countryList);
		model.addAttribute(HPEB2BStorefrontConstant.REGISTER, new HPEB2BRegisterForm());
		languagesData(model);
		return getDefaultRegistrationPage(model);
	}


	/**
	 * @param countryIsoCode
	 * @param model
	 * @return
	 */
	@GetMapping(value = "/regions", produces = "application/json")
	@ResponseBody
	public List<RegionData> regionForCountry(@RequestParam("isocode") final String countryIsoCode, final Model model)
	{
		final List<RegionData> finalRegionList = hpeRegionFacade.findRegionforCountry(countryIsoCode);
		return finalRegionList;
	}

	/**
	 * @param email
	 * @param model
	 * @return
	 */
	@GetMapping(value = "/resendEmail", produces = "application/json")
	@ResponseBody
	private String resendEmail(@RequestParam("email") final String email, final Model model)
	{
		boolean isEmailSent = false;

		if (email != null)
		{
			isEmailSent = hpeCustomerFacade.resendEmail(email);
		}

		if (isEmailSent)
		{
			return HPEB2BStorefrontConstant.SUCCESS;
		}
		return null;
	}

	/**
	 * @param b2bunit
	 * @param email
	 * @param model
	 * @return
	 */
	@GetMapping(value = "/b2bunit", produces = "application/json")
	@ResponseBody
	private String validateB2BUnit(@RequestParam("b2bunit") final String b2bunit, @RequestParam("email") final String email,
			final Model model)
	{
		boolean isDomain = false;

		if (email != null)
		{
			final String customerExist = hpeCustomerFacade.getCustomerInRegistrationInprogress(email);
			if (customerExist != null)
			{
				return HPEB2BStorefrontConstant.INROGRESS_CUSTOMER;
			}
			if (hpeCustomerFacade.isExistingCustomer(email))
			{
				return HPEB2BStorefrontConstant.CUSTOMER_EXIST;
			}
		}

		if (b2bunit != null && !(b2bunit.isEmpty()))
		{
			/** final B2BUnitModel b2bunitModel = hPEB2BUnitDao.getB2BUnitForUid(b2bunit); **/
			final B2BUnitModel b2bunitModel = b2BCommerceUnitService.getUnitForUid(b2bunit);
			if (b2bunitModel != null)
			{
				if (b2bunitModel.getReportingOrganization() != null)
				{
					isDomain = HPERegistrationUtil.b2bDomainValidator(b2bunitModel, email);

					if (!isDomain)
					{
						return HPEB2BStorefrontConstant.INVALID_DOMAIN;
					}
					else
					{
						return HPEB2BStorefrontConstant.SUCCESS;
					}
				}
				else
				{
					return HPEB2BStorefrontConstant.INVALID_PORTALPIN;
				}
			}
			else
			{
				return HPEB2BStorefrontConstant.INVALID_PORTALPIN;
			}
		}
		else
		{
			return HPEB2BStorefrontConstant.SUCCESS;
		}
	}

	/**
	 * @param model
	 * @param request
	 * @param response
	 * @param token
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	@GetMapping(value = "/emailverification")
	public String validateCustomer(final Model model, final HttpServletRequest request, final HttpServletResponse response,
			@RequestParam(name = "token") final String token) throws CMSItemNotFoundException

	{
		final CustomerModel customer = hpeCustomerFacade.getCustomerForToken(token);
		String webSiteURL = null;
		if (customer != null)
		{
			if (customer instanceof B2BCustomerModel)
			{
				webSiteURL = Config.getParameter("website.hpeSiteB2B");
			}
			else
			{
				webSiteURL = Config.getParameter("website.hpeSiteB2C");
			}
			model.addAttribute("siteURL", webSiteURL);
			model.addAttribute("customername", customer.getName());
		}

		storeCmsPageInModel(model, getCmsPageForRegistration());
		return ControllerConstants.Views.Pages.Account.RegistrationConfirmationPage;
	}


	/**
	 * @return
	 * @throws CMSItemNotFoundException
	 */
	protected AbstractPageModel getCmsPageForRegistration() throws CMSItemNotFoundException
	{
		return getContentPageForLabelOrId("registerconfirmation");
	}

	/**
	 * Address Doctor Integration- Method to Get the Address Suggestions from Address Doctor
	 *
	 * @param form
	 * @param bindingResult
	 * @param model
	 * @param request
	 * @param response
	 * @param redirectModel
	 * @return
	 * @throws Exception
	 */
	@PostMapping(value = "/addressVerification", produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody String addressDoctor(@ModelAttribute(HPEB2BStorefrontConstant.REGISTER) final HPEB2BRegisterForm form,
			final BindingResult bindingResult, final Model model, final HttpServletRequest request,
			final HttpServletResponse response, final RedirectAttributes redirectModel) throws Exception
	{
		final HPERegisterInputForm hpeRegisterInputForm = new HPERegisterInputForm();
		hpeRegisterInputForm.setCountryCode(form.getCountryCode());
		hpeRegisterInputForm.setAddress1(form.getAddress1());
		hpeRegisterInputForm.setAddress2(form.getAddress2());
		hpeRegisterInputForm.setCity(form.getCity());
		hpeRegisterInputForm.setStateCode(form.getStateCode());
		hpeRegisterInputForm.setZipCode(form.getZipCode());
		hpeRegisterInputForm.setFirstName(form.getFirstName());
		hpeRegisterInputForm.setLastName(form.getLastName());
		hpeRegisterInputForm.setEmailAddress(form.getEmail());
		if (TRUE.equalsIgnoreCase(Config.getParameter(GTS_CHECK_ENABLED)))
		{
			final String redirectUrl = Config.getString(GTS_FAIL_REDIRECT_URL, DEFAULT_GTS_FAIL_REDIRECT_URL);
			final GTSUtil gtsUtil = new GTSUtil();
			final HpeAddressForm hpeAddressForm = gtsUtil.convertRegisterToAddressForm(hpeRegisterInputForm);
			final GTSRequestData gtsRequest = gtsUtil.createGTSRequestData(null, hpeAddressForm, null);
			final boolean gtsResponse = hpeUserFacade.getGTSResponse(gtsRequest);
			if (LOG.isDebugEnabled())
			{
				LOG.debug("gtsResponse::: " + gtsResponse);
			}
			if (!gtsResponse)
			{
				final JSONObject redirectToHpe = new JSONObject();
				redirectToHpe.put("redirectURL", redirectUrl);
				return redirectToHpe.toString();
			}
		}
		return addressDoctor(hpeRegisterInputForm, model);
	}

	private String addressDoctor(final HPERegisterInputForm hpeRegisterInputForm, final Model model)
			throws com.fasterxml.jackson.core.JsonGenerationException, JsonMappingException, IOException
	{
		//Address Doctor Integration Response
		final String addressDoctorResponse = hpeAddressDoctorIntegrationFacade.hpeAddressDoctorIntegration(hpeRegisterInputForm,
				model);
		if (addressDoctorResponse != null)
		{
			return addressDoctorResponse;
		}
		else
		{
			if (LOG.isDebugEnabled())
			{
				LOG.debug("RegisterPageController...addressDoctor()..Address Doctor Response is Null");
			}
		}
		return addressDoctorResponse;
	}

	@PostMapping(value = "/addressVerificationForInvite", produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody String addressDoctorVerification(
			@ModelAttribute(HPEB2BStorefrontConstant.INVITEBYEMAILCUSTOMERFORM) final InviteByEmailCustomerForm form,
			final BindingResult bindingResult, final Model model, final HttpServletRequest request,
			final HttpServletResponse response, final RedirectAttributes redirectModel) throws CMSItemNotFoundException, IOException
	{
		final HPERegisterInputForm hpeRegisterInputForm = new HPERegisterInputForm();
		hpeRegisterInputForm.setCountryCode(form.getCountryCode());
		hpeRegisterInputForm.setAddress1(form.getAddress1());
		hpeRegisterInputForm.setAddress2(form.getAddress2());
		hpeRegisterInputForm.setCity(form.getCity());
		hpeRegisterInputForm.setStateCode(form.getStateCode());
		hpeRegisterInputForm.setZipCode(form.getZipCode());

		return addressDoctor(hpeRegisterInputForm, model);
	}

	/**
	 * Register New User to hpestorefront usingn HPE Passport
	 *
	 * @param bindingResult
	 * @param form
	 * @param model
	 * @param request
	 * @param response
	 * @param redirectModel
	 * @return redirect to homepage on success.
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 * @throws KeyManagementException
	 * @exception JsonGenerationException,JsonMappingException,IOException,CMSItemNotFoundException
	 *
	 */
	@PostMapping(value = "/register")
	public ResponseEntity<String> registerUser(@ModelAttribute(HPEB2BStorefrontConstant.REGISTER) final HPEB2BRegisterForm form,
			final BindingResult bindingResult, final Model model, final HttpServletRequest request,
			final HttpServletResponse response, final RedirectAttributes redirectModel)
			throws CMSItemNotFoundException, IOException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException
	{

		final B2BUnitModel b2bunit = setB2BUnit(form.getPortalPin());


		final HPERegisterInputForm hpeRegisterInputForm = new HPERegisterInputForm();

		hpeRegisterInputForm.setConfirmPassword(form.getCheckPwd());
		hpeRegisterInputForm.setEmailAddress(form.getEmail());
		hpeRegisterInputForm.setFirstName(form.getFirstName());
		hpeRegisterInputForm.setLastName(form.getLastName());
		hpeRegisterInputForm.setNewPassword(form.getPwd());
		hpeRegisterInputForm.setUserId(form.getEmail());
		hpeRegisterInputForm.setContactByEmail(form.getEmailCheck());
		hpeRegisterInputForm.setCountryCode(form.getCountryCode());

		final ResponseEntity<String> hpeRegistrationData = hpePassportIntegrationFacade.hpeRegisterUser(hpeRegisterInputForm);

		if (hpeRegistrationData != null && hpeRegistrationData.getBody() != null)
		{
			if (hpeRegistrationData.getStatusCode() == HttpStatus.OK
					&& hpeRegistrationData.getBody().contains(HPEB2BStorefrontConstant.PROFILE_IDENTITY))
			{
				/** b2bunit.getReportingOrganization() == null added to check if Global B2BUnit redirect to b2c flow **/
				if (b2bunit == null || b2bunit.getReportingOrganization() == null
						&& !b2bunit.getUid().equalsIgnoreCase(ROOT)) /** create b2c customer without auto login in b2b scenario **/
				{
					createCustomer(null, form, bindingResult, model, request, response, redirectModel);
				}
				else /** create b2b customer without auto login in b2b scenario **/
				{
					/** b2bunit = hpeRegistrationUtil.b2bRegistrationValidator(b2bunit, form.getCountryCode()); **/
					createB2BCustomer(null, form, b2bunit, bindingResult, model, request, response, redirectModel);
				}
				if (baseStoreService.getCurrentBaseStore().getUid().contains("b2c"))
				{
					processRegisterUserRequest(null, form, bindingResult, model, request, response, redirectModel);
				}
				return new ResponseEntity<>(hpeRegistrationData.getBody(), HttpStatus.OK);
			}
			else
			{
				return new ResponseEntity<>(hpeRegistrationData.getBody(), HttpStatus.BAD_REQUEST);
			}
		}
		else
		{
			return new ResponseEntity<>("Invalid Username or Password", HttpStatus.BAD_REQUEST);
		}
	}

	private B2BUnitModel setB2BUnit(final String portalPin)
	{
		if (portalPin != null && !portalPin.isEmpty())
		{
			return b2BCommerceUnitService.getUnitForUid(portalPin);
		}
		else
		{
			return b2BCommerceUnitService.getUnitForUid(Config.getString(HPEB2BControllerConstants.ROOT_B2BUNIT_KEY, ROOT));
		}
	}

	/**
	 * @return the baseStoreService
	 */
	public BaseStoreService getBaseStoreService()
	{
		return baseStoreService;
	}

	/**
	 * @param baseStoreService
	 *           the baseStoreService to set
	 */
	public void setBaseStoreService(final BaseStoreService baseStoreService)
	{
		this.baseStoreService = baseStoreService;
	}

	@GetMapping(value = "/newcustomer-email")
	public String doRegisterEmailCustomer(final Model model, final HttpServletRequest request, final HttpServletResponse response,
			@RequestParam(name = "token") final String token) throws CMSItemNotFoundException
	{
		getContentPageforid(model, HPEB2BControllerConstants.REGISTRATION_RETURNING_EMAIL_INVITATION_CMS_PAGE);
		final InviteByEmailCustomerForm inviteByEmailCustomerForm = new InviteByEmailCustomerForm();
		if (!StringUtils.isEmpty(token))
		{
			final B2BCustomerModel customer = hPEB2BUserFacade.getUserfromToken(token);
			if (customer != null)
			{
				inviteByEmailCustomerForm.setB2bUnit(customer.getDefaultB2BUnit().getUid());
				inviteByEmailCustomerForm.setCustomer(customer.getDefaultB2BUnit().getReportingOrganization().getUid());
				inviteByEmailCustomerForm.setEmailIds(customer.getUid());
				model.addAttribute(HPEB2BControllerConstants.CUSTOMER_STRING, setCustomer(customer.getDefaultB2BUnit()));
				/** model.addAttribute(HPEB2BControllerConstants.B2BUNIT_STRING, customer.getDefaultB2BUnit().getLocName()); **/
				String role = StringUtils.EMPTY;
				/**
				 * final boolean gpcommgroup = false; final boolean managefavorites = false;
				 **/
				final Set<PrincipalGroupModel> groups = customer.getGroups();
				final StringBuilder b2bUnits = new StringBuilder();

				for (final PrincipalGroupModel group : groups)
				{
					/**
					 * if (group.getUid().equalsIgnoreCase(HPEB2BControllerConstants.GPCOMMGROUP_STRING)) { gpcommgroup = true; } if
					 * (group.getUid().equalsIgnoreCase(HPEB2BControllerConstants.MANAGEFAVORITESGROUP_STRING)) { managefavorites =
					 * true; }
					 **/
					String temp = null;
					if (group instanceof B2BUnitModel && b2bUnits.toString().isEmpty())
					{
						temp = appendB2Bunit(group);
						if (temp != null)
						{
							b2bUnits.append(temp);
						}
					}
					else if (group instanceof B2BUnitModel && !b2bUnits.toString().isEmpty())
					{
						temp = appendB2Bunit(group);
						if (temp != null)
						{
							b2bUnits.append(HPEB2BControllerConstants.COMMA).append(temp);
						}
					}

					if (group.getUid().equalsIgnoreCase(HPEB2BControllerConstants.MANAGERGROUP_STRING)
							|| group.getUid().equalsIgnoreCase(HPEB2BControllerConstants.PURCHASEORDERGROUP_STRING)
							|| group.getUid().equalsIgnoreCase(HPEB2BControllerConstants.REQUESTERGROUP_STRING))
					{
						role = group.getName();
					}
				}
				model.addAttribute(HPEB2BControllerConstants.B2BUNIT_STRING, b2bUnits.toString());
				model.addAttribute(HPEB2BControllerConstants.ROLE_STRING, role);
				/**
				 * model.addAttribute(HPEB2BControllerConstants.GPCOMMGROUP_STRING, gpcommgroup);
				 * model.addAttribute(HPEB2BControllerConstants.MANAGEFAVORITES_STRING, managefavorites);
				 **/
				model.addAttribute(HPEB2BControllerConstants.INVITEBYEMAILCUSTOMERFORM_STRING, inviteByEmailCustomerForm);
				final List<CountryData> countryList = hpeCountryFacade.findCountries();
				countryList.sort(Comparator.comparing(CountryData::getName, Comparator.nullsFirst(Comparator.naturalOrder())));
				model.addAttribute(HPEB2BStorefrontConstant.COUNTRY_LIST, countryList);
				languagesData(model);
			}
			else
			{
				GlobalMessages.addErrorMessage(model, "Customer Not Found");
			}
		}
		else
		{
			GlobalMessages.addErrorMessage(model, "Token is Empty");
		}

		return getViewForPage(model);
	}

	private String setCustomer(final B2BUnitModel b2bunit)
	{
		String customer = StringUtils.EMPTY;
		if (b2bunit.getReportingOrganization() != null && b2bunit.getReportingOrganization().getReportingOrganization() != null)
		{
			if (b2bunit.getReportingOrganization().getReportingOrganization().getDisplayName() != null)
			{
				customer = b2bunit.getReportingOrganization().getReportingOrganization().getDisplayName();
			}
			else if (b2bunit.getReportingOrganization().getReportingOrganization().getLocName() != null)
			{
				customer = b2bunit.getReportingOrganization().getReportingOrganization().getLocName();
			}
		}
		else
		{
			if (b2bunit.getReportingOrganization().getDisplayName() != null)
			{
				customer = b2bunit.getReportingOrganization().getDisplayName();
			}
			else if (b2bunit.getReportingOrganization().getLocName() != null)
			{
				customer = b2bunit.getReportingOrganization().getLocName();
			}
		}
		return customer;
	}

	private String appendB2Bunit(final PrincipalGroupModel group)
	{
		if (group.getDisplayName() != null)
		{
			return group.getDisplayName();
		}
		else if (group.getLocName() != null)
		{
			return group.getLocName();
		}
		return null;
	}

	private void languagesData(final Model model)
	{
		final List<LanguageData> languagesData = hPEB2BUserFacade.getLanguagesForCurrentBaseStore();
		model.addAttribute(HPEB2BControllerConstants.LANGUAGEDATA_STRING, languagesData);
	}

	@PostMapping(value = "/newcustomer-email")
	public String doRegisterEmailCustomerPOST(final Model model, final HttpServletRequest request,
			final HttpServletResponse response, final InviteByEmailCustomerForm form, final RedirectAttributes redirectModel)
			throws CMSItemNotFoundException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, IOException
	{
		getContentPageforid(model, HPEB2BControllerConstants.REGISTRATION_RETURNING_EMAIL_INVITATION_CMS_PAGE);
		final HPERegisterInputForm hpeRegisterInputForm = new HPERegisterInputForm();
		String emailCheck;
		if (form.getEmailCheck())
		{
			emailCheck = "Y";
		}
		else
		{
			emailCheck = "N";
		}
		hpeRegisterInputForm.setConfirmPassword(form.getCheckPwd());
		hpeRegisterInputForm.setEmailAddress(form.getEmailIds());
		hpeRegisterInputForm.setFirstName(form.getFirstName());
		hpeRegisterInputForm.setLastName(form.getLastName());
		hpeRegisterInputForm.setNewPassword(form.getPwd());
		hpeRegisterInputForm.setUserId(form.getEmailIds());
		hpeRegisterInputForm.setContactByEmail(emailCheck);
		hpeRegisterInputForm.setCountryCode(form.getCountryCode());
		try
		{

			final ResponseEntity<String> hpeRegistrationData = hpePassportIntegrationFacade.hpeRegisterUser(hpeRegisterInputForm);
			if (hpeRegistrationData != null && hpeRegistrationData.getBody() != null)
			{
				if (hpeRegistrationData.getStatusCode() == HttpStatus.OK
						&& hpeRegistrationData.getBody().contains(HPEB2BStorefrontConstant.PROFILE_IDENTITY))
				{
					hPEB2BUserFacade.updateB2BCustomer(getDataFromForm(form));
					/**
					 * GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.INFO_MESSAGES_HOLDER, "SuccessFully Updated ");
					 **/
					model.addAttribute(ERROR, false);
				}
				else
				{
					/**
					 * GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					 * HPEB2BControllerConstants.ERROR_IN_UPDATING_TO_HPE_PASSPORT);
					 **/
					model.addAttribute(ERROR, true);
				}
			}
			else
			{
				/**
				 * GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
				 * HPEB2BControllerConstants.ERROR_IN_UPDATING_TO_HPE_PASSPORT);
				 **/
				model.addAttribute(ERROR, true);
			}
		}
		catch (final Exception e)
		{
			LOG.error("Exception Occured while creating customer in Hpe Passport:", e);
			model.addAttribute(ERROR, true);
			/**
			 * GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
			 * HPEB2BControllerConstants.ERROR_IN_UPDATING_TO_HPE_PASSPORT);
			 **/
		}


		return HPEB2BControllerConstants.INVITE_EMAIL_JSP_PATH;

	}

	@GetMapping(value = "/validateEmailOnGTS", produces = "application/json")
	@ResponseBody
	private String validateEmailOnGTS(@RequestParam("email") final String email, final HttpServletRequest request,
			final HttpServletResponse response) throws Exception
	{
		if (TRUE.equalsIgnoreCase(Config.getParameter(GTS_CHECK_ENABLED)))
		{
			final String redirectUrl = Config.getString(GTS_FAIL_REDIRECT_URL, DEFAULT_GTS_FAIL_REDIRECT_URL);
			final GTSUtil gtsUtil = new GTSUtil();
			final String remoteAddress = gtsUtil.remoteAddress(request);
			final GTSRequestData gtsRequest = gtsUtil.createGTSRequestData(email, null, remoteAddress);
			final boolean gtsResponse = hpeUserFacade.getGTSResponse(gtsRequest);
			if (LOG.isDebugEnabled())
			{
				LOG.debug("gtsResponse value in RegisterPageController::::::: " + gtsResponse);
			}
			if (!gtsResponse)
			{
				return redirectUrl;
			}
		}
		return HPEB2BStorefrontConstant.SUCCESS;
	}

	private HPERegisterData getDataFromForm(final InviteByEmailCustomerForm form)
	{
		final HPERegisterData data = new HPERegisterData();
		data.setFirstName(form.getFirstName());
		data.setLastName(form.getLastName());
		data.setLogin(form.getEmailIds());
		data.setPassword(form.getPwd());
		data.setTitleCode(form.getTitleCode());
		data.setAddress1(form.getAddress1());
		data.setAddress2(form.getAddress2());
		data.setCity(form.getCity());
		data.setCompany(form.getCompany());
		data.setAttentionTo(form.getAttentionTo());
		data.setCountryCode(form.getCountryCode());
		data.setZipCode(form.getZipCode());
		data.setStateCode(form.getStateCode());
		data.setPrefLang(form.getPrefLang());
		return data;

	}

	private void getContentPageforid(final Model model, final String labelOrId) throws CMSItemNotFoundException
	{
		final ContentPageModel cmsPage = getContentPageForLabelOrId(labelOrId);
		storeCmsPageInModel(model, cmsPage);
		setUpMetaDataForContentPage(model, cmsPage);
		model.addAttribute(HPEB2BControllerConstants.BREADCRUMBS_ATTR, accountBreadcrumbBuilder.getBreadcrumbs(null));
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
	}


}