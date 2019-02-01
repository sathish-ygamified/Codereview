/**
 *
 */
package com.hpe.controllers.pages;

import de.hybris.platform.acceleratorstorefrontcommons.annotations.RequireHardLogIn;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.Breadcrumb;
import de.hybris.platform.acceleratorstorefrontcommons.breadcrumb.ResourceBreadcrumbBuilder;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.ThirdPartyConstants;
import de.hybris.platform.acceleratorstorefrontcommons.controllers.pages.AbstractPageController;
import de.hybris.platform.b2b.model.B2BCustomerModel;
import de.hybris.platform.b2b.model.B2BUnitModel;
import de.hybris.platform.b2b.services.B2BUnitService;
import de.hybris.platform.b2bcommercefacades.company.data.B2BUnitData;
import de.hybris.platform.cms2.exceptions.CMSItemNotFoundException;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commercefacades.customer.CustomerFacade;
import de.hybris.platform.core.model.security.PrincipalGroupModel;
import de.hybris.platform.servicelayer.i18n.I18NService;
import de.hybris.platform.servicelayer.user.UserService;
import de.hybris.platform.util.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.hpe.b2bstorefront.forms.InviteByEmailForm;
import com.hpe.controllers.HPEB2BControllerConstants;
import com.hpe.controllers.HPEB2BStorefrontConstant;
import com.hpe.core.b2bunit.service.HPEB2BUnitService;
import com.hpe.core.enums.CustomerStatus;
import com.hpe.facades.b2bunit.HPEB2BUnitFacade;
import com.hpe.facades.form.data.InviteByEmailFormData;
import com.hpe.facades.user.HPEB2BUserFacade;
import com.hpe.facades.user.data.HPEUserGroupData;
import com.hpe.facades.util.HPERegistrationUtil;


/**
 * @author AN294922
 *
 */
@Controller
@RequireHardLogIn
@RequestMapping(value = "/admin")
public class B2BAdminController extends AbstractPageController
{

	private static final Logger log = Logger.getLogger(B2BAdminController.class);



	@Resource(name = "hPEB2BUnitFacade")
	private HPEB2BUnitFacade hPEB2BUnitFacade;

	@Resource(name = "accountBreadcrumbBuilder")
	private ResourceBreadcrumbBuilder accountBreadcrumbBuilder;

	@Resource(name = "userService")
	private UserService userService;

	@Resource(name = "b2bUnitService")
	private B2BUnitService<B2BUnitModel, B2BCustomerModel> b2bUnitService;

	@Resource(name = "customerFacade")
	private CustomerFacade customerFacade;

	@Resource(name = "hPEB2BUserFacade")
	private HPEB2BUserFacade hPEB2BUserFacade;

	@Resource(name = "hPEB2BUnitService")
	private HPEB2BUnitService hPEB2BUnitService;

	@Resource(name = "hpeRegistrationUtil")
	public HPERegistrationUtil hpeRegistrationUtil;

	@Resource(name = "i18nService")
	private I18NService i18nService;

	@Resource(name = "messageSource")
	private MessageSource messageSource;




	@GetMapping(value = "/email-invite")
	public String doAdmin(final Model model, final HttpServletRequest request, final HttpServletResponse response,
			final RedirectAttributes redirectModel) throws CMSItemNotFoundException
	{
		adminPage(model, HPEB2BControllerConstants.REGISTRATION_EMAIL_INVITATION_CMS_PAGE);
		final B2BCustomerModel customer = hPEB2BUserFacade.getCurrentCustomer();

		if (customer != null)
		{
			final Set<PrincipalGroupModel> userGroups = customer.getGroups();
			List<B2BUnitData> customers = new ArrayList<>();
			final List<B2BUnitData> b2bUnits = new ArrayList<>();
			final List<B2BUnitData> partyIds = new ArrayList<>();
			final List<HPEUserGroupData> roles = hPEB2BUserFacade.getRoles();
			model.addAttribute(HPEB2BControllerConstants.ROLES_STRING, roles);
			final String check = adminCheck(userGroups);
			if (check.equalsIgnoreCase(HPEB2BControllerConstants.HPEADMIN_STRING))
			{
				customers = hPEB2BUnitFacade.getAllParentB2Bunits();
				/** b2bUnits = getB2bUnitsforUid(customers.get(0).getUid()); **/
				model.addAttribute(HPEB2BControllerConstants.HPEADMIN_STRING, Boolean.TRUE);
			}
			else if (check.equalsIgnoreCase(HPEB2BControllerConstants.B2BADMIN_STRING))
			{
				setB2BUnitForB2BAdmin(customers, b2bUnits, partyIds, customer, model);
			}
			model.addAttribute(HPEB2BControllerConstants.B2BUNITS_STRING, b2bUnits);
			model.addAttribute(HPEB2BControllerConstants.CUSTOMERS_STRING, customers);
			model.addAttribute(HPEB2BControllerConstants.PARTYIDS_STRING, partyIds);
		}
		model.addAttribute(HPEB2BControllerConstants.INVITEBYEMAILFORM_STRING, new InviteByEmailForm());
		return getViewForPage(model);
	}

	/**
	 * @param userGroups
	 * @param ishpeAdmin
	 * @param isb2bAdmin
	 */
	private String adminCheck(final Set<PrincipalGroupModel> userGroups)
	{
		for (final PrincipalGroupModel user : userGroups)
		{
			if (user.getUid()
					.equals(Config.getString(HPEB2BControllerConstants.HPEADMIN_KEY, HPEB2BControllerConstants.HPEADMIN_STRING)))
			{
				return HPEB2BControllerConstants.HPEADMIN_STRING;
			}
			if (user.getUid()
					.equals(Config.getString(HPEB2BControllerConstants.B2BADMIN_KEY, HPEB2BControllerConstants.B2BADMIN_STRING)))
			{
				return HPEB2BControllerConstants.B2BADMIN_STRING;
			}
		}
		return StringUtils.EMPTY;
	}

	private void setB2BUnitForB2BAdmin(final List<B2BUnitData> customers, final List<B2BUnitData> b2bUnits,
			final List<B2BUnitData> partyIds, final B2BCustomerModel customer, final Model model)
	{
		int b2bunitsCount = 0;
		int partyIdsCount = 0;
		if (customer.getDefaultB2BUnit() != null)
		{
			B2BUnitData b2bunit;
			final List<B2BUnitData> b2bUnitsData;
			List<B2BUnitData> partyIdsData;
			if (customer.getDefaultB2BUnit().getReportingOrganization() != null)
			{

				if (customer.getDefaultB2BUnit().getReportingOrganization().getReportingOrganization() != null)
				{
					b2bunit = hPEB2BUnitFacade
							.getB2BUnitDataForModel(customer.getDefaultB2BUnit().getReportingOrganization().getReportingOrganization());
					customers.add(b2bunit);
					b2bunit = hPEB2BUnitFacade.getB2BUnitDataForModel(customer.getDefaultB2BUnit().getReportingOrganization());
					b2bUnits.add(b2bunit);
					b2bunit = hPEB2BUnitFacade.getB2BUnitDataForModel(customer.getDefaultB2BUnit());
					partyIds.add(b2bunit);
					b2bunitsCount = 1;
					partyIdsCount = 1;
				}
				else
				{
					b2bunit = hPEB2BUnitFacade.getB2BUnitDataForModel(customer.getDefaultB2BUnit().getReportingOrganization());
					customers.add(b2bunit);
					b2bunit = hPEB2BUnitFacade.getB2BUnitDataForModel(customer.getDefaultB2BUnit());
					b2bUnits.add(b2bunit);
					partyIdsData = getB2bUnitsforUid(customer.getDefaultB2BUnit().getUid());
					partyIds.addAll(partyIdsData);
					b2bunitsCount = 1;
					partyIdsCount = partyIds.size();
				}
			}
			else
			{
				b2bunit = hPEB2BUnitFacade.getB2BUnitDataForModel(customer.getDefaultB2BUnit());
				customers.add(b2bunit);
				b2bUnitsData = getB2bUnitsforUid(customer.getDefaultB2BUnit().getUid());
				b2bUnits.addAll(b2bUnitsData);
				partyIdsData = Collections.emptyList();
				partyIds.addAll(partyIdsData);
				b2bunitsCount = b2bUnits.size();
			}
		}

		model.addAttribute(HPEB2BControllerConstants.B2BUNITCOUNT, b2bunitsCount);
		model.addAttribute(HPEB2BControllerConstants.PARTYIDSCOUNT, partyIdsCount);
		model.addAttribute(HPEB2BControllerConstants.HPEADMIN_STRING, Boolean.FALSE);
	}

	private InviteByEmailFormData convertFormtoData(final InviteByEmailForm inviteByEmailForm)
	{
		final InviteByEmailFormData inviteByEmailFormData = new InviteByEmailFormData();
		inviteByEmailFormData.setB2bUnit(inviteByEmailForm.getB2bUnit());
		inviteByEmailFormData.setCustomer(inviteByEmailForm.getCustomer());
		inviteByEmailFormData.setPartyid(inviteByEmailForm.getPartyId());
		inviteByEmailFormData.setEmailIds(inviteByEmailForm.getEmailIds());
		inviteByEmailFormData.setGpcommAccess(inviteByEmailForm.isGpcommAccess());
		inviteByEmailFormData.setManageCompanyFavorites(inviteByEmailForm.isManageCompanyFavorites());
		inviteByEmailFormData.setRole(inviteByEmailForm.getRole());
		return inviteByEmailFormData;
	}

	private boolean domainValidationforInvite(final String b2bunits, final String email)
	{
		boolean isValidDomain = false;
		final String[] b2bUnitsArray = b2bunits.split(HPEB2BControllerConstants.COMMA);
		for (final String b2bunit : b2bUnitsArray)
		{
			final B2BUnitModel b2bunitModel = hPEB2BUnitFacade.getB2BUnitForUid(b2bunit);
			if (b2bunitModel != null)
			{
				isValidDomain = hpeRegistrationUtil.b2bDomainValidator(b2bunitModel, email);
				if (isValidDomain)
				{
					break;
				}
			}
		}
		return isValidDomain;
	}

	@PostMapping(value = "/email-invite")
	public String doInviteByEmail(final Model model, final HttpServletRequest request, final HttpServletResponse response,
			final RedirectAttributes redirectModel, final InviteByEmailForm inviteByEmailForm) throws CMSItemNotFoundException
	{
		if (inviteByEmailForm != null)
		{
			final InviteByEmailFormData inviteByEmailFormData = convertFormtoData(inviteByEmailForm);
			final String[] emailids = inviteByEmailForm.getEmailIds().split(HPEB2BControllerConstants.COMMA);
			final StringBuilder alreadyRegistered = new StringBuilder();
			final String b2bunit = getB2BUnitsfromFormData(inviteByEmailForm);
			for (final String email : emailids)
			{
				final boolean isValidDomain = domainValidationforInvite(b2bunit, email);
				if (isValidDomain)
				{
					if (userService.isUserExisting(email))
					{
						final B2BCustomerModel b2bCustomer = userService.getUserForUID(email, B2BCustomerModel.class);
						if (b2bCustomer.getRegistrationStatus().equals(CustomerStatus.REGISTERED))
						{
							invalidID(alreadyRegistered, email);
							log.info(email + " is Already Registered");
							continue;
						}
					}
					inviteByEmailFormData.setEmailIds(email);
					hPEB2BUserFacade.createB2BCustomer(inviteByEmailFormData);
				}
				else
				{
					invalidID(alreadyRegistered, email);
				}
			}
			updateModel(alreadyRegistered, model);
		}
		return doAdmin(model, request, response, redirectModel);
	}

	private String getB2BUnitsfromFormData(final InviteByEmailForm inviteByEmailForm)
	{
		String b2bunit = StringUtils.EMPTY;
		if (inviteByEmailForm.getPartyId() != null)
		{
			b2bunit = inviteByEmailForm.getPartyId();
		}
		else if (inviteByEmailForm.getB2bUnit() != null)
		{
			b2bunit = inviteByEmailForm.getB2bUnit();
		}
		else if (inviteByEmailForm.getCustomer() != null)
		{
			b2bunit = inviteByEmailForm.getCustomer();
		}
		return b2bunit;
	}

	private void updateModel(final StringBuilder alreadyRegistered, final Model model)
	{
		if (!alreadyRegistered.toString().isEmpty())
		{
			model.addAttribute(HPEB2BControllerConstants.ERROR, true);
		}
		else
		{
			model.addAttribute(HPEB2BControllerConstants.ERROR, false);
		}
	}

	private StringBuilder invalidID(final StringBuilder alreadyRegistered, final String email)
	{
		if (alreadyRegistered.toString().isEmpty())
		{
			alreadyRegistered.append(email);
		}
		else
		{
			alreadyRegistered.append(HPEB2BControllerConstants.COMMA).append(email);
		}
		return alreadyRegistered;
	}

	private List<B2BUnitData> getB2bUnitsforUid(final String uID)
	{
		return hPEB2BUnitFacade.getAllChildB2BunitsOfOrganisationForUid(uID);
	}

	@ResponseBody
	@PostMapping(value = "/getB2BUnits")
	public Map<String, Object> doGetB2BUnits(
			@ModelAttribute(HPEB2BControllerConstants.INVITEBYEMAILFORM_STRING) final InviteByEmailForm inviteByEmailForm,
			final HttpServletRequest request)
	{
		final String customer = request.getParameter("uID");
		final Map<String, Object> b2bUnitsMap = new HashMap<>();
		final List<B2BUnitData> b2bUnits = getB2bUnitsforUid(customer);
		for (final B2BUnitData b2bunit : b2bUnits)
		{
			b2bUnitsMap.put(b2bunit.getUid(), b2bunit.getName());
		}
		return b2bUnitsMap;

	}


	private void adminPage(final Model model, final String labelOrId) throws CMSItemNotFoundException
	{
		final ContentPageModel adminCMSPage = getContentPageForLabelOrId(labelOrId);
		storeCmsPageInModel(model, adminCMSPage);
		setUpMetaDataForContentPage(model, adminCMSPage);
		final List<Breadcrumb> breadcrumbs = new ArrayList<>();
		breadcrumbs.add(new Breadcrumb(
				Config.getString(HPEB2BControllerConstants.BREADCRUMB_USERS_KEY_CONFIG,
						HPEB2BControllerConstants.BREADCRUMB_USERS_DEF_CONFIG),
				messageSource.getMessage(HPEB2BControllerConstants.BREADCRUMB_USERS_KEY, null, i18nService.getCurrentLocale()),
				null));
		breadcrumbs.addAll(accountBreadcrumbBuilder.getBreadcrumbs(HPEB2BControllerConstants.BREADCRUMB_SENDREGISTRATION));
		model.addAttribute(HPEB2BControllerConstants.BREADCRUMBS_ATTR, breadcrumbs);
		model.addAttribute(ThirdPartyConstants.SeoRobots.META_ROBOTS, ThirdPartyConstants.SeoRobots.NOINDEX_NOFOLLOW);
	}

	@GetMapping(value = "/resendEmail", produces = "application/json")
	@ResponseBody
	private String resendEmail(@RequestParam("email") final String email, final Model model)
	{
		boolean isEmailSent = false;

		if (email != null)
		{
			isEmailSent = hPEB2BUserFacade.resendEmailForRegistrationByInvite(email);
		}

		if (isEmailSent)
		{
			return HPEB2BStorefrontConstant.SUCCESS;
		}
		return null;
	}


}
