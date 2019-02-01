/**
 *
 */
package com.hpe.controllers.pages;

import de.hybris.platform.acceleratorstorefrontcommons.consent.data.ConsentCookieData;
import de.hybris.platform.acceleratorstorefrontcommons.constants.WebConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractRegisterPageController;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.util.GlobalMessages;
import de.hybris.platform.acceleratorstorefrontcommons.forms.ConsentForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.GuestForm;
import de.hybris.platform.acceleratorstorefrontcommons.forms.LoginForm;
import de.hybris.platform.b2b.model.B2BUnitModel;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.AbstractPageModel;
import de.hybris.platform.commerceservices.customer.DuplicateUidException;
import de.hybris.platform.jalo.JaloSession;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.WebUtils;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.hpe.controllers.HPEB2BStorefrontConstant;
import com.hpe.facades.hpepassport.HPEPassportIntegrationFacade;
import com.hpe.facades.registration.HPECustomerFacade;
import com.hpe.facades.registration.data.HPERegisterData;
import com.hpe.hpepassport.constant.HPEPassportConstant;
import com.hpe.security.HPEB2BRegisterForm;



/**
 * New HPEAbstractRegisterPageController Overriding the OOTB Flow
 *
 * @author Nandhini Marimuthu
 * @version 1.0
 */
public class HPEAbstractRegisterPageController extends AbstractRegisterPageController
{

	private static final Logger LOGGER = Logger.getLogger(HPEAbstractRegisterPageController.class);


	@Resource(name = "hpeCustomerFacade")
	HPECustomerFacade hpeCustomerFacade;
	@Resource(name = "hpePassportIntegrationFacade")
	public HPEPassportIntegrationFacade hpePassportIntegrationFacade;
	
	private static final String YES = "Y";

	protected String processRegisterUserRequest(final String referer, final HPEB2BRegisterForm form,
			final BindingResult bindingResult, final Model model, final HttpServletRequest request,
			final HttpServletResponse response, final RedirectAttributes redirectModel) throws CMSItemNotFoundException // NOSONAR
			, JsonGenerationException, JsonMappingException, IOException, KeyManagementException, KeyStoreException,
			NoSuchAlgorithmException
	{
		if (bindingResult.hasErrors())
		{
			form.setTermsCheck(false);
			model.addAttribute(form);
			model.addAttribute(new LoginForm());
			model.addAttribute(new GuestForm());
			GlobalMessages.addErrorMessage(model, HPEB2BStorefrontConstant.FORM_GLOBAL_ERROR);
			return handleRegistrationError(model);
		}

		final HPERegisterData data = new HPERegisterData();
		data.setFirstName(form.getFirstName());
		data.setLastName(form.getLastName());
		data.setLogin(form.getEmail());
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

		final String username = form.getEmail();
		final String password = form.getPwd();
		try
		{
			getHpeCustomerFacade().register(data);
			//Login to HPE Passport After Successful Registration in Hybris DB
			final ResponseEntity<String> loginResponseEntity = hpePassportIntegrationFacade.getHPEPassportLogin(username, password);
			if (loginResponseEntity != null && loginResponseEntity.getStatusCode() == HttpStatus.OK
					&& loginResponseEntity.getBody().contains(HPEB2BStorefrontConstant.LOGIN_SESSIONTOKEN))
			{
				JaloSession.getCurrentSession().setAttribute(HPEPassportConstant.SESSIONTOKEN, loginResponseEntity.getBody());
				final Object session = JaloSession.getCurrentSession().getAttribute(HPEPassportConstant.SESSIONTOKEN);
				LOGGER.info("***** JaloSession Object*************" + session);
				getAutoLoginStrategy().login(form.getEmail().toLowerCase(), form.getPwd(), request, response);
				GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.CONF_MESSAGES_HOLDER,
						HPEB2BStorefrontConstant.REG_CONFIRMATION);
			}


		}
		catch (final DuplicateUidException e)
		{
			LOGGER.warn("registration failed: ", e);
			form.setTermsCheck(false);
			model.addAttribute(form);
			model.addAttribute(new LoginForm());
			model.addAttribute(new GuestForm());
			bindingResult.rejectValue(HPEB2BStorefrontConstant.EMAIL, HPEB2BStorefrontConstant.REG_ACCOUNT_EXISTS_ERROR);
			GlobalMessages.addErrorMessage(model, HPEB2BStorefrontConstant.FORM_GLOBAL_ERROR);
			return handleRegistrationError(model);
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
			LOGGER.error("Error occurred while creating consents during registration", e);
			GlobalMessages.addFlashMessage(redirectModel, GlobalMessages.ERROR_MESSAGES_HOLDER,
					HPEB2BStorefrontConstant.CONSENT_FORM_GLOBAL_ERROR);
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
				LOGGER.error(String.format("Cookie Data could not be decoded : %s", cookie.getValue()), e);
			}
			catch (final IOException e)
			{
				LOGGER.error("Cookie Data could not be mapped into the Object", e);
			}
			catch (final Exception e)
			{
				LOGGER.error("Error occurred while creating Anonymous cookie consents", e);
			}
		}

		customerConsentDataStrategy.populateCustomerConsentDataInSession();

		return REDIRECT_PREFIX + getSuccessRedirect(request, response);
	}

	public String createCustomer(final String referer, final HPEB2BRegisterForm form, final BindingResult bindingResult,
			final Model model, final HttpServletRequest request, final HttpServletResponse response,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		try
		{
			getHpeCustomerFacade().register(populateRegisterData(form));
			return HPEB2BStorefrontConstant.SUCCESS;
		}
		catch (final DuplicateUidException e)
		{
			LOGGER.warn("registration failed: ", e);
			model.addAttribute(form);
			model.addAttribute(new LoginForm());
			model.addAttribute(new GuestForm());
			bindingResult.rejectValue(HPEB2BStorefrontConstant.EMAIL, HPEB2BStorefrontConstant.REG_ACCOUNT_EXISTS_ERROR);
			GlobalMessages.addErrorMessage(model, HPEB2BStorefrontConstant.FORM_GLOBAL_ERROR);
			return handleRegistrationError(model);
		}


	}

	public String createB2BCustomer(final String referer, final HPEB2BRegisterForm form, final B2BUnitModel b2bunit,
			final BindingResult bindingResult, final Model model, final HttpServletRequest request,
			final HttpServletResponse response, final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		try
		{
			getHpeCustomerFacade().registerB2bCustomer(populateRegisterData(form), b2bunit);
			return HPEB2BStorefrontConstant.SUCCESS;
		}
		catch (final DuplicateUidException e)
		{
			LOGGER.warn("registration failed: ", e);
			model.addAttribute(form);
			model.addAttribute(new LoginForm());
			model.addAttribute(new GuestForm());
			bindingResult.rejectValue(HPEB2BStorefrontConstant.EMAIL, HPEB2BStorefrontConstant.REG_ACCOUNT_EXISTS_ERROR);
			GlobalMessages.addErrorMessage(model, HPEB2BStorefrontConstant.FORM_GLOBAL_ERROR);
			return handleRegistrationError(model);
		}

	}


	public HPERegisterData populateRegisterData(final HPEB2BRegisterForm form)
	{

		final HPERegisterData data = new HPERegisterData();
		data.setFirstName(form.getFirstName());
		data.setLastName(form.getLastName());
		data.setLogin(form.getEmail());
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
		if(form.getEmailCheck().equalsIgnoreCase(YES))
      {
          data.setIsNewsletterOptedIn(Boolean.TRUE);
      }
		if (form.getPortalPin() != null)
		{
			data.setPortalPin(form.getPortalPin());
		}
		data.setRegisterStatus(HPEB2BStorefrontConstant.DEFAULT_REGISTRTION_STATUS);
		return data;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractRegisterPageController#getCmsPage()
	 */
	@Override
	protected AbstractPageModel getCmsPage() throws CMSItemNotFoundException
	{
		// XXX Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractRegisterPageController#
	 * getSuccessRedirect(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected String getSuccessRedirect(final HttpServletRequest request, final HttpServletResponse response)
	{
		// XXX Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractRegisterPageController#getView()
	 */
	@Override
	protected String getView()
	{
		// XXX Auto-generated method stub
		return null;
	}

	/**
	 * @return the hpeCustomerFacade
	 */
	public HPECustomerFacade getHpeCustomerFacade()
	{
		return hpeCustomerFacade;
	}

	/**
	 * @param hpeCustomerFacade
	 *           the hpeCustomerFacade to set
	 */
	public void setHpeCustomerFacade(final HPECustomerFacade hpeCustomerFacade)
	{
		this.hpeCustomerFacade = hpeCustomerFacade;
	}

}
