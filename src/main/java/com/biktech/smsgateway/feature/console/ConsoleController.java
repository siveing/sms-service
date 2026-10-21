package com.biktech.smsgateway.feature.console;

import com.biktech.smsgateway.common.GatewayException;
import com.biktech.smsgateway.config.GatewayProperties;
import com.biktech.smsgateway.feature.caller.Caller;
import com.biktech.smsgateway.feature.caller.CallerKind;
import com.biktech.smsgateway.feature.caller.CallerService;
import com.biktech.smsgateway.feature.message.DeliveryChannel;
import com.biktech.smsgateway.feature.message.MessageDtos;
import com.biktech.smsgateway.feature.message.MessageService;
import com.biktech.smsgateway.feature.message.PhoneNumbers;
import com.biktech.smsgateway.feature.message.SmsStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.UUID;

/** The built-in console: overview, messages, a test send, and the apps and phones allowed in. */
@Controller
@RequiredArgsConstructor
public class ConsoleController {

    private final ConsoleService console;
    private final MessageService messages;
    private final CallerService callers;
    private final GatewayProperties properties;

    @ModelAttribute("username")
    public String username(Principal admin) {
        return admin == null ? null : admin.getName();
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("signInOn", console.signInOn());
        return "login";
    }

    @GetMapping("/")
    public String overview(Model model) {
        model.addAttribute("overview", console.overview());
        return "overview";
    }

    @GetMapping("/messages")
    public String messages(
            @RequestParam(required = false) SmsStatus status,
            @RequestParam(required = false) String number,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        model.addAttribute("result", console.messages(status, number, page));
        model.addAttribute("status", status);
        model.addAttribute("number", number);
        model.addAttribute("statuses", SmsStatus.values());
        return "messages";
    }

    @ModelAttribute("channels")
    public DeliveryChannel[] channels() {
        return DeliveryChannel.values();
    }

    @GetMapping("/send")
    public String sendForm(Model model) {
        model.addAttribute("form", new ConsoleViews.SendForm());
        model.addAttribute("telegram", console.telegram());
        return "send";
    }

    @PostMapping("/send")
    public String send(
            @Valid @ModelAttribute("form") ConsoleViews.SendForm form,
            BindingResult result,
            Principal admin,
            Model model,
            RedirectAttributes flash) {
        model.addAttribute("telegram", console.telegram());
        if (result.hasErrors()) {
            return "send";
        }
        DeliveryChannel channel = form.getChannel() == null ? DeliveryChannel.SMS : form.getChannel();
        try {
            MessageDtos.MessageView sent = messages.send(null, "Console · " + admin.getName(), channel,
                new MessageDtos.SendRequest(form.getTo(), form.getText(), null, form.getValidSeconds()));
            flash.addFlashAttribute("notice", "Queued for " + PhoneNumbers.masked(sent.to()) + (channel == DeliveryChannel.TELEGRAM
                ? ". The bot posts it to the Telegram group within seconds."
                : ". A phone that is online picks it up within seconds."));
            return "redirect:/messages";
        } catch (GatewayException ex) {
            result.rejectValue("to", "invalid", ex.getMessage());
            return "send";
        }
    }

    @GetMapping("/apps")
    public String apps(Model model) {
        model.addAttribute("rows", console.apps());
        model.addAttribute("form", new ConsoleViews.NameForm());
        model.addAttribute("telegram", console.telegram());
        if (model.getAttribute("issued") instanceof CallerService.IssuedKey issued) {
            model.addAttribute("example", "curl -X POST " + baseUrl() + "/api/v1/messages \\\n"
                + "  -H 'X-Api-Key: " + issued.key() + "' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  -d '{\"to\": \"012345678\", \"text\": \"Hello from the gateway\"}'");
        }
        return "apps";
    }

    @GetMapping("/phones")
    public String phones(Model model) {
        model.addAttribute("rows", console.phones());
        model.addAttribute("form", new ConsoleViews.NameForm());
        if (model.getAttribute("issued") instanceof CallerService.IssuedKey issued) {
            String base = baseUrl();
            model.addAttribute("setupCommand",
                "curl -sO " + base + "/phone/sms-phone.sh && SERVER=" + base + " PHONE_KEY=" + issued.key() + " bash sms-phone.sh");
        }
        return "phones";
    }

    @PostMapping("/apps")
    public String addApp(@Valid @ModelAttribute("form") ConsoleViews.NameForm form, BindingResult result,
                         RedirectAttributes flash) {
        return add(CallerKind.CLIENT, form, result, flash);
    }

    @PostMapping("/phones")
    public String addPhone(@Valid @ModelAttribute("form") ConsoleViews.NameForm form, BindingResult result,
                           RedirectAttributes flash) {
        return add(CallerKind.PHONE, form, result, flash);
    }

    @PostMapping("/apps/{id}/key")
    public String newAppKey(@PathVariable UUID id, RedirectAttributes flash) {
        flash.addFlashAttribute("issued", callers.newKey(id));
        return "redirect:/apps";
    }

    @PostMapping("/phones/{id}/key")
    public String newPhoneKey(@PathVariable UUID id, RedirectAttributes flash) {
        flash.addFlashAttribute("issued", callers.newKey(id));
        return "redirect:/phones";
    }

    @PostMapping("/apps/{id}/enabled")
    public String enableApp(@PathVariable UUID id, @RequestParam boolean enabled, RedirectAttributes flash) {
        return setEnabled(id, enabled, "/apps", flash);
    }

    @PostMapping("/apps/{id}/channel")
    public String appChannel(@PathVariable UUID id, @RequestParam DeliveryChannel channel, RedirectAttributes flash) {
        Caller app = callers.setChannel(id, channel);
        flash.addFlashAttribute("notice", channel == DeliveryChannel.TELEGRAM
            ? app.getName() + " now goes to Telegram: its messages are posted to the test group, not texted."
            : app.getName() + " now sends real SMS from the phones.");
        return "redirect:/apps";
    }

    @PostMapping("/phones/{id}/enabled")
    public String enablePhone(@PathVariable UUID id, @RequestParam boolean enabled, RedirectAttributes flash) {
        return setEnabled(id, enabled, "/phones", flash);
    }

    private String add(CallerKind kind, ConsoleViews.NameForm form, BindingResult result, RedirectAttributes flash) {
        String page = kind == CallerKind.PHONE ? "/phones" : "/apps";
        if (result.hasErrors()) {
            flash.addFlashAttribute("error", result.getFieldErrors().getFirst().getDefaultMessage());
            return "redirect:" + page;
        }
        try {
            flash.addFlashAttribute("issued", callers.create(kind, form.getName()));
        } catch (GatewayException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + page;
    }

    private String setEnabled(UUID id, boolean enabled, String page, RedirectAttributes flash) {
        Caller caller = callers.setEnabled(id, enabled);
        flash.addFlashAttribute("notice", caller.getName() + (enabled
            ? " is enabled again."
            : " is disabled. Its key is refused until you enable it."));
        return "redirect:" + page;
    }

    private String baseUrl() {
        String publicUrl = properties.getPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl.trim().replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
