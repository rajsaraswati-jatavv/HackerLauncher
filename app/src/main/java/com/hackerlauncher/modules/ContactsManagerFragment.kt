package com.hackerlauncher.modules

import com.hackerlauncher.R
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ContactsManagerFragment : Fragment() {

    private lateinit var searchInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var tvContactCount: TextView
    private lateinit var btnExport: MaterialButton

    private val contactsList = mutableListOf<ContactInfo>()
    private val filteredList = mutableListOf<ContactInfo>()
    private lateinit var contactsAdapter: ContactsAdapter

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadContacts() else showToast("READ_CONTACTS permission required")
    }

    data class ContactInfo(
        val id: Long,
        val name: String,
        val phoneNumbers: List<String>,
        val emails: List<String>,
        val photoUri: String?,
        val organization: String?,
        val lastUpdated: Long
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_contacts_manager, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchInput = view.findViewById(R.id.searchInput)
        recyclerView = view.findViewById(R.id.contactsRecycler)
        emptyState = view.findViewById(R.id.emptyState)
        tvContactCount = view.findViewById(R.id.tvContactCount)
        btnExport = view.findViewById(R.id.btnExport)

        contactsAdapter = ContactsAdapter(filteredList) { contact ->
            showContactDetail(contact)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = contactsAdapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterContacts(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnExport.setOnClickListener { exportVCard() }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadContacts()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val contacts = mutableListOf<ContactInfo>()

                val projection = arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                    ContactsContract.Contacts.LAST_TIME_CONTACTED
                )

                val cursor = requireContext().contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    while (it.moveToNext()) {
                        val id = it.getLong(idIndex)
                        val name = it.getString(nameIndex) ?: "Unknown"
                        val photoUri = it.getString(photoIndex)
                        val hasPhone = it.getInt(hasPhoneIndex) > 0

                        val phoneNumbers = if (hasPhone) getPhoneNumbers(id) else emptyList()
                        val emails = getEmails(id)
                        val organization = getOrganization(id)

                        contacts.add(
                            ContactInfo(
                                id = id,
                                name = name,
                                phoneNumbers = phoneNumbers,
                                emails = emails,
                                photoUri = photoUri,
                                organization = organization,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    contactsList.clear()
                    contactsList.addAll(contacts)
                    filteredList.clear()
                    filteredList.addAll(contacts)
                    contactsAdapter.notifyDataSetChanged()
                    tvContactCount.text = "${contacts.size} contacts"
                    emptyState.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun getPhoneNumbers(contactId: Long): List<String> {
        val numbers = mutableListOf<String>()
        val cursor = requireContext().contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )
        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                it.getString(numberIndex)?.let { num -> numbers.add(num) }
            }
        }
        return numbers
    }

    private fun getEmails(contactId: Long): List<String> {
        val emails = mutableListOf<String>()
        val cursor = requireContext().contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.DATA),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )
        cursor?.use {
            val emailIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA)
            while (it.moveToNext()) {
                it.getString(emailIndex)?.let { email -> emails.add(email) }
            }
        }
        return emails
    }

    private fun getOrganization(contactId: Long): String? {
        val cursor = requireContext().contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Organization.COMPANY),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null
        )
        cursor?.use {
            val companyIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Organization.COMPANY)
            if (it.moveToFirst()) return it.getString(companyIndex)
        }
        return null
    }

    private fun filterContacts(query: String) {
        val q = query.lowercase(Locale.getDefault())
        filteredList.clear()
        if (q.isEmpty()) {
            filteredList.addAll(contactsList)
        } else {
            filteredList.addAll(contactsList.filter { contact ->
                contact.name.lowercase(Locale.getDefault()).contains(q) ||
                contact.phoneNumbers.any { it.contains(q) } ||
                contact.emails.any { it.lowercase(Locale.getDefault()).contains(q) }
            })
        }
        contactsAdapter.notifyDataSetChanged()
        emptyState.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showContactDetail(contact: ContactInfo) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_contact_detail, null)

        val tvName: TextView = sheetView.findViewById(R.id.tvContactName)
        val tvOrg: TextView = sheetView.findViewById(R.id.tvContactOrg)
        val ivPhoto: ImageView = sheetView.findViewById(R.id.ivContactPhoto)
        val phonesLayout: LinearLayout = sheetView.findViewById(R.id.phonesLayout)
        val emailsLayout: LinearLayout = sheetView.findViewById(R.id.emailsLayout)
        val chipCall: Chip = sheetView.findViewById(R.id.chipCall)
        val chipDial: Chip = sheetView.findViewById(R.id.chipDial)
        val chipSms: Chip = sheetView.findViewById(R.id.chipSms)

        tvName.text = contact.name
        tvOrg.text = contact.organization ?: ""
        tvOrg.visibility = if (contact.organization != null) View.VISIBLE else View.GONE

        // Load photo
        if (contact.photoUri != null) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(Uri.parse(contact.photoUri))
                val bitmap = BitmapFactory.decodeStream(inputStream)
                ivPhoto.setImageBitmap(bitmap)
                ivPhoto.visibility = View.VISIBLE
            } catch (e: Exception) {
                ivPhoto.visibility = View.GONE
            }
        } else {
            ivPhoto.visibility = View.GONE
        }

        // Phone numbers
        phonesLayout.removeAllViews()
        contact.phoneNumbers.forEach { phone ->
            val tv = TextView(requireContext()).apply {
                text = phone
                setTextColor(android.graphics.Color.parseColor("#00FF00"))
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 8, 0, 8)
                textSize = 14f
            }
            phonesLayout.addView(tv)
        }

        // Emails
        emailsLayout.removeAllViews()
        contact.emails.forEach { email ->
            val tv = TextView(requireContext()).apply {
                text = email
                setTextColor(android.graphics.Color.parseColor("#00AA00"))
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 8, 0, 8)
                textSize = 14f
            }
            emailsLayout.addView(tv)
        }

        val primaryPhone = contact.phoneNumbers.firstOrNull()
        chipCall.setOnClickListener {
            primaryPhone?.let { phone ->
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone")))
            } ?: showToast("No phone number")
        }
        chipDial.setOnClickListener {
            primaryPhone?.let { phone ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } ?: showToast("No phone number")
        }
        chipSms.setOnClickListener {
            primaryPhone?.let { phone ->
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")))
            } ?: showToast("No phone number")
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun exportVCard() {
        if (contactsList.isEmpty()) {
            showToast("No contacts to export")
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val vcardBuilder = StringBuilder()
                    for (contact in contactsList) {
                        vcardBuilder.append("BEGIN:VCARD\n")
                        vcardBuilder.append("VERSION:3.0\n")
                        vcardBuilder.append("FN:${contact.name}\n")
                        contact.phoneNumbers.forEach { phone ->
                            vcardBuilder.append("TEL:$phone\n")
                        }
                        contact.emails.forEach { email ->
                            vcardBuilder.append("EMAIL:$email\n")
                        }
                        contact.organization?.let { org ->
                            vcardBuilder.append("ORG:$org\n")
                        }
                        vcardBuilder.append("END:VCARD\n")
                    }

                    val exportDir = File(requireContext().getExternalFilesDir(null), "exports")
                    exportDir.mkdirs()
                    val file = File(exportDir, "contacts_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.vcf")
                    OutputStreamWriter(FileOutputStream(file)).use { writer ->
                        writer.write(vcardBuilder.toString())
                    }

                    withContext(Dispatchers.Main) {
                        showToast("Exported ${contactsList.size} contacts to ${file.name}")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Export failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    // --- Inner Adapter ---

    inner class ContactsAdapter(
        private val items: List<ContactInfo>,
        private val onClick: (ContactInfo) -> Unit
    ) : RecyclerView.Adapter<ContactsAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvContactName)
            val tvPhone: TextView = view.findViewById(R.id.tvContactPhone)
            val tvOrg: TextView = view.findViewById(R.id.tvContactOrg)
            val ivPhoto: ImageView = view.findViewById(R.id.ivContactPhoto)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvPhone.text = item.phoneNumbers.firstOrNull() ?: "No phone"
            holder.tvOrg.text = item.organization ?: ""
            holder.tvOrg.visibility = if (item.organization != null) View.VISIBLE else View.GONE

            if (item.photoUri != null) {
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(Uri.parse(item.photoUri))
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    holder.ivPhoto.setImageBitmap(bitmap)
                    holder.ivPhoto.visibility = View.VISIBLE
                } catch (e: Exception) {
                    holder.ivPhoto.visibility = View.GONE
                }
            } else {
                holder.ivPhoto.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
